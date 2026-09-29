$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if (-not (Test-Path '.env')) { throw '缺少 .env' }
$envMap = @{}
Get-Content '.env' | ForEach-Object { if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$' -and $matches[1] -notlike '#*') { $envMap[$matches[1]] = $matches[2].Trim().Trim('"') } }
$db = if ($envMap.MYSQL_DATABASE) { $envMap.MYSQL_DATABASE } else { 'fang_hu' }
$password = if ($envMap.MYSQL_ROOT_PASSWORD) { $envMap.MYSQL_ROOT_PASSWORD } else { throw '缺少 MYSQL_ROOT_PASSWORD' }
docker compose up -d mysql
docker compose exec -T mysql mysqladmin ping -uroot "-p$password" --wait=30 | Out-Null
Write-Host "MySQL 已就绪：$db"
Write-Host '生产数据库结构由独立 backend 的 Flyway 迁移管理；请启动 backend 完成迁移。'
