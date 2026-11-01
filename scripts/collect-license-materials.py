import io
import json
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path


LEGAL_NAME = re.compile(r"^(?:LICENSE|LICENCE|COPYING|NOTICE|COPYRIGHT)(?:$|[._-])", re.IGNORECASE)
LICENSE_NAME = re.compile(r"^(?:LICENSE|LICENCE|COPYING)(?:$|[._-])", re.IGNORECASE)
IMAGES = {"app": "mocklab-app:1.0.0", "runner": "mocklab-runner:1.0.0"}


def legal_files(directory, recursive=True):
    files = directory.rglob("*") if recursive else directory.iterdir()
    return sorted(
        path for path in files
        if path.is_file() and not path.is_symlink() and LEGAL_NAME.match(path.name)
    )


def copy_legal_files(source, destination, all_files=False, recursive=True):
    files = (
        sorted(path for path in source.rglob("*") if path.is_file() and not path.is_symlink())
        if all_files else legal_files(source, recursive)
    )
    for path in files:
        target = destination / path.relative_to(source)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(path, target)
    return len(files)


def collect_frontend(root, destination, missing):
    packages = json.loads((root / "frontend/package-lock.json").read_text())["packages"]
    modules = root / "frontend"
    rows = ["package\tversion\tdeclared_license\tlegal_files\tlicense_text_found"]
    for location, package in sorted(packages.items()):
        if not location.startswith("node_modules/") or ".." in location.split("/"):
            continue
        name = location.rsplit("node_modules/", 1)[-1]
        installed = modules / location
        if not installed.is_dir() and package.get("optional"):
            continue
        target = destination / location
        count = copy_legal_files(installed, target, recursive=False) if installed.is_dir() else 0
        has_license = installed.is_dir() and any(
            LICENSE_NAME.match(path.name) for path in legal_files(installed, recursive=False)
        )
        rows.append(f"{name}\t{package['version']}\t{package.get('license', 'UNKNOWN')}\t{count}\t{int(has_license)}")
        if not has_license:
            missing.append(f"npm: {name}@{package['version']} ({location})")
    (destination / "packages.tsv").parent.mkdir(parents=True, exist_ok=True)
    (destination / "packages.tsv").write_text("\n".join(rows) + "\n")


def collect_jar(contents, name, destination, rows, missing):
    count = 0
    license_count = 0
    with zipfile.ZipFile(io.BytesIO(contents)) as archive:
        for entry in archive.infolist():
            parts = entry.filename.split("/")
            if entry.is_dir() or any(part in ("", ".", "..") for part in parts):
                continue
            if not LEGAL_NAME.match(parts[-1]):
                continue
            target = destination / name / Path(*parts)
            target.parent.mkdir(parents=True, exist_ok=True)
            with archive.open(entry) as source, target.open("wb") as output:
                shutil.copyfileobj(source, output)
            count += 1
            license_count += bool(LICENSE_NAME.match(parts[-1]))
    rows.append(f"{name}\t{count}\t{license_count}")
    if not license_count and name not in ("app.jar", "runner.jar"):
        missing.append(f"Java: {destination.parent.name}/{name}")


def collect_java(image, directory, destination, missing):
    rows = ["jar\tlegal_files\tlicense_texts"]
    jar_name = "app.jar" if image == "app" else "runner.jar"
    jar = directory / jar_name
    contents = jar.read_bytes()
    collect_jar(contents, jar_name, destination, rows, missing)
    if image == "app":
        with zipfile.ZipFile(io.BytesIO(contents)) as archive:
            for entry in archive.infolist():
                if entry.filename.startswith("BOOT-INF/lib/") and entry.filename.endswith(".jar"):
                    collect_jar(archive.read(entry), Path(entry.filename).name, destination, rows, missing)
    else:
        for dependency in sorted((directory / "lib").glob("*.jar")):
            collect_jar(dependency.read_bytes(), dependency.name, destination, rows, missing)
    (destination / "jars.tsv").write_text("\n".join(rows) + "\n")


def collect_os_packages(status, docs, destination, image, missing):
    rows = ["package\tversion\tcopyright_file"]
    for block in status.read_text().split("\n\n"):
        fields = dict(line.split(": ", 1) for line in block.splitlines() if ": " in line)
        if fields.get("Status") != "install ok installed" or "Package" not in fields:
            continue
        name = fields["Package"]
        copyright_path = docs / name / "copyright"
        copyright_file = copyright_path.is_file() and not copyright_path.is_symlink()
        rows.append(f"{name}\t{fields['Version']}\t{int(copyright_file)}")
        if not copyright_file:
            missing.append(f"OS: {image}/{name}@{fields['Version']}")
    (destination / "os-packages.tsv").write_text("\n".join(rows) + "\n")


def copy_from(container, source, destination):
    subprocess.run(["docker", "cp", f"{container}:{source}", str(destination)], check=True)


def collect_image(image, tag, destination, missing):
    container = subprocess.check_output(
        ["docker", "create", "--user", "0", "--entrypoint", "sh", tag], text=True
    ).strip()
    try:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            image_id = subprocess.check_output(
                ["docker", "image", "inspect", "--format", "{{.Id}}", tag], text=True
            ).strip()
            destination.mkdir(parents=True, exist_ok=True)
            (destination / "image-id.txt").write_text(image_id + "\n")
            copy_from(container, "/usr/share/doc", root / "doc")
            copy_from(container, "/usr/share/common-licenses", root / "common-licenses")
            copy_from(container, "/opt/java/openjdk/legal", root / "jre")
            copy_from(container, "/var/lib/dpkg/status", root / "dpkg-status")
            copy_from(container, f"/app/{'app.jar' if image == 'app' else 'runner.jar'}", root / ("app.jar" if image == "app" else "runner.jar"))
            if image == "runner":
                copy_from(container, "/app/lib", root / "lib")
            copy_legal_files(root / "doc", destination / "os")
            copy_legal_files(root / "common-licenses", destination / "common-licenses", all_files=True)
            if not copy_legal_files(root / "jre", destination / "jre", all_files=True):
                missing.append(f"image: {tag} has no JRE legal files")
            shutil.copyfile(root / "dpkg-status", destination / "dpkg-status")
            collect_os_packages(root / "dpkg-status", root / "doc", destination, image, missing)
            collect_java(image, root, destination / "java", missing)
    finally:
        subprocess.run(["docker", "rm", container], check=True)


def main():
    root = Path(__file__).resolve().parent.parent
    destination = Path(sys.argv[1]).resolve()
    destination.mkdir(parents=True, exist_ok=False)
    missing = []
    collect_frontend(root, destination / "frontend", missing)
    for image, tag in IMAGES.items():
        collect_image(image, tag, destination / "images" / image, missing)
    (destination / "REVIEW.txt").write_text(
        "Collected license evidence is not a complete compliance determination.\n"
        "The npm list includes build-time packages; verify the final dist contents.\n"
        "Review Java JARs without embedded legal files and the license obligations of\n"
        "all installed OS packages, including corresponding source requirements.\n\n"
        "Items without collected legal files (verify upstream materials):\n"
        + ("\n".join(missing) if missing else "None found") + "\n"
    )
    print(f"Collected license evidence; {len(missing)} packages need manual review")


if __name__ == "__main__":
    main()
