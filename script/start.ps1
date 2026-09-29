$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if (-not (Test-Path '.env')) { throw '缺少 .env，请先复制 .env.example 并填写本地密码' }
docker compose up -d
if ($LASTEXITCODE -ne 0) { throw 'Docker 服务启动失败' }
Write-Host '基础服务已启动。运行 .\script\health.ps1 检查状态。'
