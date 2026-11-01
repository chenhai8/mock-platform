#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
npm ci --prefix frontend --no-audit --no-fund
npm run build --prefix frontend
mvn -B package
