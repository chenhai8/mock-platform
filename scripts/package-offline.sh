#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p release
if [ -e release/mocklab-images-1.0.0.tar ]; then echo 'Archive already exists; choose another release directory' >&2; exit 1; fi
# dist/ is not needed for Docker images, but it must be built for offline Nginx setup
[ -d dist ] || { echo 'dist/ not found. Run "cd frontend && npm ci && npm run build" first' >&2; exit 1; }
docker compose build
docker save -o release/mocklab-images-1.0.0.tar mocklab-app:1.0.0 mocklab-runner:1.0.0
sha256sum release/mocklab-images-1.0.0.tar > release/mocklab-images-1.0.0.sha256
printf 'Copy image archive, checksum, compose.yml, deploy/, scripts/, documentation and dist/ to the offline server
'
