$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
& .\mvnw.cmd -f backend\pom.xml package
if ($LASTEXITCODE -ne 0) { throw '后端构建失败' }
