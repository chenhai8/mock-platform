#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p backups
backup="backups/mocklab-$(date +%Y%m%d-%H%M%S).sql"
# --result-file is intentionally omitted so the dump remains on the host
if docker compose exec -T db sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysqldump -u "$MYSQL_USER" --single-transaction --no-tablespaces --set-gtid-purged=OFF mocklab' > "$backup"; then
    printf 'Saved backup: %s
' "$backup"
else
    printf 'Backup failed; incomplete file retained for inspection: %s
' "$backup" >&2
    exit 1
fi
