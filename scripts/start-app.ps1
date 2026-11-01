$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)
java -jar backend/target/backend-1.0.0.jar --server.address=127.0.0.1
