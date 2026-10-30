#!/usr/bin/env bash
set -euo pipefail
umask 077

# 将脚本与 GitHub 下载的 ZIP 放在同一目录，执行 bash prepare-offline.sh，无需传参
# 下载包固定命名为 mocklab-offline-linux-amd64.zip，浏览器添加的 (1) 等后缀需先去掉
# 全部处理成功后删除旧的同名 .zip.bak，再将本次 ZIP 改名为 .zip.bak，失败则保留原包
# compose.yml、.env 和 deploy/env.example 在脚本同级目录按需创建，已存在则保留
# 脚本仅导入后端镜像和补齐配置，不处理前端、不启动或重启容器
main() {
    # 第一步：定位脚本同级目录中的固定 ZIP，确定部署目录和备份路径
    [[ $# -eq 0 ]] || fail 'No arguments needed; place mocklab-offline-linux-amd64.zip beside this script'
    destination="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
    archive="$destination/mocklab-offline-linux-amd64.zip"
    backup="${archive}.bak"
    [[ -f "$archive" ]] || fail "ZIP file not found: $archive"

    # 第二步：检查所需命令和 Docker 服务，未就绪时停止处理
    for tool in unzip tar sha256sum docker mktemp; do
        command -v "$tool" >/dev/null || fail "Required command not found: $tool"
    done
    docker info >/dev/null

    # 第三步：创建独立临时目录，脚本退出时自动清理，不影响部署目录
    work_dir=$(mktemp -d "${TMPDIR:-/tmp}/mocklab-offline.XXXXXXXX")
    trap 'rm -rf -- "$work_dir"' EXIT

    # 第四步：读取镜像包、配置包和校验清单，并检查两个包的 SHA-256
    # 仅按固定名称读取归档内容，不直接解压归档中的目录、链接或任意路径
    for name in mocklab-images.tar.gz mocklab-config.tar.gz SHA256SUMS; do
        unzip -p "$archive" "$name" > "$work_dir/$name"
    done
    verify_archive mocklab-images.tar.gz
    verify_archive mocklab-config.tar.gz

    # 第五步：提取部署所需的配置文件到临时目录，暂不覆盖现有配置
    tar -xOzf "$work_dir/mocklab-config.tar.gz" compose.yml > "$work_dir/compose.yml"
    tar -xOzf "$work_dir/mocklab-config.tar.gz" deploy/env.example > "$work_dir/env.example"
    [[ -s "$work_dir/compose.yml" && -s "$work_dir/env.example" ]] || fail 'Empty deployment configuration'

    # 第六步：导入 app 和 runner 镜像，失败时保留本次 ZIP 和上次备份
    printf 'Loading app and runner images...\n'
    docker load -i "$work_dir/mocklab-images.tar.gz"

    # 第七步：仅补齐缺失配置，已有 compose.yml、环境变量模板和 .env 保持不变
    mkdir -p -- "$destination/deploy"
    copy_if_missing "$work_dir/compose.yml" "$destination/compose.yml"
    copy_if_missing "$work_dir/env.example" "$destination/deploy/env.example"
    copy_if_missing "$destination/deploy/env.example" "$destination/.env"
    docker image ls

    # 第八步：前面全部成功后，删除旧备份并将本次 ZIP 改名为 .zip.bak
    # 仅替换同级目录的普通备份文件，不递归删除目录或跟随符号链接
    if [[ -e "$backup" || -L "$backup" ]]; then
        [[ -f "$backup" && ! -L "$backup" ]] || fail "Backup path is not a regular file: $backup"
        rm -- "$backup"
    fi
    mv -- "$archive" "$backup"
    printf 'ZIP backed up: %s\n' "$backup"

    # 第九步：提示手动核对配置和启动容器，脚本不自动执行部署命令
    printf '\nImages imported; existing configuration preserved.\n'
    printf 'Deployment directory: %s\n' "$destination"
    printf 'Review .env and compose.yml, then run in that directory:\n'
    printf '  docker compose config --quiet\n'
    printf '  docker compose up -d --no-build --pull never\n'
    printf 'No containers were started or restarted; frontend files were not deployed.\n'
}

# 辅助方法：输出错误并退出，防止后续步骤继续执行
fail() {
    printf 'ERROR: %s\n' "$*" >&2
    exit 1
}

# 辅助方法：按固定包名核对校验清单，拒绝缺失、重复或不匹配的校验值
verify_archive() {
    local name="$1" expected='' digest entry actual
    while read -r digest entry; do
        if [[ "$entry" == "$name" || "$entry" == "*$name" ]]; then
            [[ "$digest" =~ ^[[:xdigit:]]{64}$ ]] || fail "Invalid checksum for $name"
            [[ -z "$expected" ]] || fail "Duplicate checksum for $name"
            expected="${digest,,}"
        fi
    done < "$work_dir/SHA256SUMS"
    [[ -n "$expected" ]] || fail "Missing checksum for $name"
    actual=$(sha256sum -- "$work_dir/$name")
    [[ "${actual%% *}" == "$expected" ]] || fail "Checksum mismatch: $name"
    printf 'Verified: %s\n' "$name"
}

# 辅助方法：仅在目标不存在时复制，保留现有文件及符号链接
copy_if_missing() {
    local source="$1" target="$2"
    if [[ -e "$target" || -L "$target" ]]; then
        printf 'Preserved existing: %s\n' "$target"
        return
    fi
    cp -n -- "$source" "$target"
    printf 'Created: %s\n' "$target"
}

# 所有函数定义完成后再执行主流程，避免 Bash 调用尚未定义的函数
main "$@"
