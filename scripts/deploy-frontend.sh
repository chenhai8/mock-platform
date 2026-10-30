#!/usr/bin/env bash
set -euo pipefail
umask 022

# 修改这里为本项目的 Nginx 静态目录，不要指向其他站点或 Nginx 配置目录
NGINX_HTML_DIR="/usr/share/nginx/html/mocklab"

# 将脚本与 mocklab-dist.tar.gz 放在同一目录，执行 bash deploy-frontend.sh，无需传参
# 同名静态文件会被覆盖，旧的其他文件不会删除，前端包保留便于再次使用
# 不修改 Nginx 配置、不重载 Nginx、不操作后端容器

# 第一步：定位脚本同级目录中的前端包，不依赖执行命令时所在的目录
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
archive="$script_dir/mocklab-dist.tar.gz"
if [[ ! -f "$archive" ]]; then
    printf 'ERROR: Frontend archive not found: %s\n' "$archive" >&2
    exit 1
fi

# 第二步：检查静态目录配置，避免将前端文件写入根目录或相对路径
if [[ "$NGINX_HTML_DIR" != /* || "$NGINX_HTML_DIR" == / ]]; then
    printf 'ERROR: NGINX_HTML_DIR must be an absolute site directory, not /\n' >&2
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

# 第四步：将解压后的内容复制到静态目录，不额外套一层 dist 目录
mkdir -p -- "$NGINX_HTML_DIR"
cp -R -- "$work_dir/." "$NGINX_HTML_DIR/"
printf 'Frontend files deployed to: %s\n' "$NGINX_HTML_DIR"
printf 'Nginx configuration and backend containers were not changed.\n'
