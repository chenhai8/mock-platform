#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p release
if [ -e release/mocklab-images-1.0.0.tar ]; then echo 'Archive already exists; choose another release directory' >&2; exit 1; fi
docker compose build
docker save -o release/mocklab-images-1.0.0.tar mocklab-app:1.0.0 mocklab-runner:1.0.0
sha256sum release/mocklab-images-1.0.0.tar > release/mocklab-images-1.0.0.sha256
printf 'Copy image archive, checksum, compose.yml, deploy/, scripts/ and documentation to the offline server
'
