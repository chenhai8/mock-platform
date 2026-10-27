$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)
npm.cmd ci --prefix frontend --no-audit --no-fund
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
npm.cmd run build --prefix frontend
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
mvn.cmd -B package
exit $LASTEXITCODE
