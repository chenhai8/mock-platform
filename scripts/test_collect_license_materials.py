import hashlib
import importlib.util
import io
import json
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location("collector", Path(__file__).with_name("collect-license-materials.py"))
collector = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(collector)


class LicenseMaterialsTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.destination = self.root / "app" / "java"
        self.destination.mkdir(parents=True)
        self.rows = []
        self.missing = []
        self.materials = {"components": {}, "files": {}}

    def jar(self, entries):
        contents = io.BytesIO()
        with zipfile.ZipFile(contents, "w") as archive:
            for name, text in entries.items():
                archive.writestr(name, text)
        return contents.getvalue()

    def collect(self, contents, name="example-1.jar"):
        collector.collect_jar(contents, name, self.destination, self.rows, self.missing, self.materials)

    def register(self, contents):
        filename = "example/LICENSE"
        source = self.root / "license-materials" / filename
        source.parent.mkdir(parents=True)
        source.write_bytes(b"upstream license text")
        self.materials["components"]["example-1.jar"] = {
            "coordinate": "example:example:1",
            "sha256": hashlib.sha256(contents).hexdigest(),
            "license_expression": "MIT",
            "files": [filename],
        }
        self.materials["files"][filename] = {
            "sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
            "kind": "license",
            "url": "upstream source recorded separately",
        }
        replacement = patch.object(collector, "__file__", str(self.root / "collector.py"))
        replacement.start()
        self.addCleanup(replacement.stop)
        return source

    def test_pom_and_notice_are_not_license_texts(self):
        self.collect(self.jar({
            "META-INF/maven/example/example/pom.xml": "<project/>",
            "META-INF/NOTICE": "notice",
            "example/Notice.class": "bytecode",
        }))
        self.assertEqual(self.rows[0].split("\t")[1:], ["1", "0", "1", "0", "0"])
        self.assertEqual(len(self.missing), 1)
        self.assertFalse((self.destination / "example-1.jar/example/Notice.class").exists())

    def test_embedded_license_is_preserved(self):
        self.collect(self.jar({"META-INF/LICENSE.txt": "license", "META-INF/NOTICE": "notice"}))
        self.assertEqual(self.rows[0].split("\t")[1:3], ["2", "1"])
        self.assertEqual(self.missing, [])

    def test_archive_paths_cannot_escape_destination(self):
        self.collect(self.jar({"../LICENSE": "bad", "/LICENSE": "bad", "META-INF/../LICENSE": "bad"}))
        self.assertEqual(self.rows[0].split("\t")[1:3], ["0", "0"])
        self.assertFalse((self.destination / "LICENSE").exists())
        self.assertFalse((self.root / "LICENSE").exists())

    def test_exact_jar_gets_verified_upstream_material(self):
        contents = self.jar({"example.class": "bytecode"})
        self.register(contents)
        self.collect(contents)
        supplemental = self.destination / "example-1.jar/supplemental"
        self.assertEqual((supplemental / "example/LICENSE").read_bytes(), b"upstream license text")
        self.assertEqual(json.loads((supplemental / "UPSTREAM.json").read_text())["component"],
                         self.materials["components"]["example-1.jar"])
        self.assertEqual(self.rows[0].split("\t")[1:], ["0", "0", "0", "1", "1"])
        self.assertEqual(self.missing, [])

    def test_changed_jar_keeps_missing_license_warning(self):
        self.register(self.jar({"example.class": "old"}))
        self.collect(self.jar({"example.class": "new"}))
        self.assertEqual(len(self.missing), 1)
        self.assertFalse((self.destination / "example-1.jar/supplemental").exists())

    def test_changed_version_cannot_reuse_license(self):
        contents = self.jar({"example.class": "bytecode"})
        self.register(contents)
        self.collect(contents, "example-2.jar")
        self.assertEqual(len(self.missing), 1)

    def test_corrupt_upstream_material_fails_collection(self):
        contents = self.jar({"example.class": "bytecode"})
        source = self.register(contents)
        source.write_bytes(b"modified")
        with self.assertRaisesRegex(ValueError, "checksum mismatch"):
            self.collect(contents)

    def test_source_metadata_alone_cannot_clear_license_warning(self):
        contents = self.jar({"example.class": "bytecode"})
        self.register(contents)
        self.materials["files"]["example/LICENSE"]["kind"] = "evidence"
        self.collect(contents)
        self.assertEqual(len(self.missing), 1)

    def test_included_source_has_a_location_notice(self):
        contents = self.jar({"example.class": "bytecode"})
        self.register(contents)
        self.materials["files"]["example/LICENSE"]["kind"] = "source"
        self.collect(contents)
        notice = self.destination / "example-1.jar/supplemental/SOURCE-AVAILABILITY.txt"
        self.assertIn("example/LICENSE", notice.read_text())
        self.assertIn("example:example:1", notice.read_text())
        self.assertEqual(len(self.missing), 1)

    def test_bundled_materials_have_verified_sources_and_checksums(self):
        root = Path(__file__).parent / "license-materials"
        materials = json.loads((root / "manifest.json").read_text())
        self.assertEqual(len(materials["components"]), 21)
        for name, record in materials["files"].items():
            with self.subTest(file=name):
                self.assertTrue(record["url"].startswith("https://"))
                self.assertEqual(hashlib.sha256((root / name).read_bytes()).hexdigest(), record["sha256"])
        for name, component in materials["components"].items():
            with self.subTest(jar=name):
                self.assertRegex(component["sha256"], r"^[0-9a-f]{64}$")
                self.assertTrue(any(materials["files"][name]["kind"] == "license" for name in component["files"]))

    def test_os_copyright_links_are_copied_only_within_docs(self):
        docs = self.root / "doc"
        (docs / "shared").mkdir(parents=True)
        (docs / "shared/copyright").write_text("copyright text")
        (docs / "linked-dir").symlink_to("shared", target_is_directory=True)
        (docs / "linked-file").mkdir()
        (docs / "linked-file/copyright").symlink_to("../shared/copyright")
        (docs / "external").symlink_to(self.root, target_is_directory=True)
        (self.root / "copyright").write_text("outside docs")
        (docs / "loop").symlink_to("loop")
        status = self.root / "dpkg-status"
        names = ("shared", "linked-dir", "linked-file", "external", "loop", "absent")
        status.write_text("\n\n".join(f"Package: {name}\nStatus: install ok installed\nVersion: 1" for name in names))
        output = self.root / "os-output"
        output.mkdir()
        missing = []
        collector.collect_os_packages(status, docs, output, "test", missing)
        for name in names[:3]:
            self.assertEqual((output / "os" / name / "copyright").read_text(), "copyright text")
        self.assertEqual(len(missing), 3)
        self.assertFalse((output / "os/external/copyright").exists())


if __name__ == "__main__":
    unittest.main()
