#!/usr/bin/env bash
set -euo pipefail
umask 022

# 修改这里为本项目的 Nginx 静态目录，目录名必须为 mocklab，不能使用其他站点或配置目录
NGINX_HTML_DIR="/usr/share/nginx/html/mocklab"

# 将脚本与 mocklab-dist.tar.gz 放在同一目录，执行 bash deploy-frontend.sh，无需传参
# 新包解压校验通过后，列出旧文件并输入 yes 确认，再清理 mocklab 目录内容并复制新文件
# 保留 mocklab 目录本身和前端包，不自动备份，清理至复制完成期间站点会短暂不可用
# 不修改 Nginx 配置、不重载 Nginx、不操作后端容器

# 第一步：定位脚本同级目录中的前端包，不依赖执行命令时所在的目录
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
archive="$script_dir/mocklab-dist.tar.gz"
if [[ ! -f "$archive" ]]; then
    printf 'ERROR: Frontend archive not found: %s\n' "$archive" >&2
    exit 1
fi

# 第二步：检查真实目标路径，禁止清理上级目录、符号链接目录或脚本所在目录
if [[ "$NGINX_HTML_DIR" != /* || -L "$NGINX_HTML_DIR" || -L "${NGINX_HTML_DIR%/}" ]]; then
    printf 'ERROR: NGINX_HTML_DIR must be an absolute directory, not a symbolic link\n' >&2
    exit 1
fi
target_dir=$(realpath -m -- "$NGINX_HTML_DIR")
if [[ "${target_dir##*/}" != mocklab || "$script_dir/" == "$target_dir/"* ]]; then
    printf 'ERROR: Target must be a dedicated mocklab directory that does not contain this script\n' >&2
    exit 1
fi

# 第三步：先解压到独立临时目录，解压失败时不覆盖正在使用的前端文件
work_dir=$(mktemp -d "${TMPDIR:-/tmp}/mocklab-frontend.XXXXXXXX")
trap 'rm -rf -- "$work_dir"' EXIT
tar -xzf "$archive" -C "$work_dir" --no-same-owner --no-same-permissions
if [[ ! -f "$work_dir/index.html" || -L "$work_dir/index.html" ]]; then
    printf 'ERROR: Frontend archive must contain a regular index.html at its root\n' >&2
    exit 1
fi

# 第四步：确认临时目录不在待清理目录内，再创建本项目静态目录
work_dir="$(cd -- "$work_dir" && pwd -P)"
if [[ "$work_dir/" == "$target_dir/"* ]]; then
    printf 'ERROR: Temporary directory must not be inside the deployment target\n' >&2
    exit 1
fi
mkdir -p -- "$target_dir"

# 第五步：展示全部旧文件，确认后仅删除 mocklab 内的内容，保留目录本身
printf 'Files to remove from %s:\n' "$target_dir"
find "$target_dir" -mindepth 1 -print
read -r -p 'Type yes to clear this directory and deploy the new frontend: ' confirmation
if [[ "$confirmation" != yes ]]; then
    printf 'Deployment cancelled; existing frontend files were not changed.\n'
    exit 1
fi
find "$target_dir" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +

# 第六步：将新文件复制到已清理的静态目录，不额外套一层 dist 目录
cp -R -- "$work_dir/." "$target_dir/"
printf 'Frontend files deployed to: %s\n' "$target_dir"
printf 'Nginx configuration and backend containers were not changed.\n'
