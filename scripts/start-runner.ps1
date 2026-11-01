$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)
java -cp "runner/target/classes;runner/target/lib/*" dev.mocklab.runner.Supervisor tcp:127.0.0.1:9091
