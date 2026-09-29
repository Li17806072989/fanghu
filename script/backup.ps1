$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$dir = Join-Path (Get-Location) 'backups'
New-Item -ItemType Directory -Force -Path $dir | Out-Null
$envMap = @{}
Get-Content '.env' | ForEach-Object { if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$') { $envMap[$matches[1]] = $matches[2].Trim().Trim('"') } }
$password = $envMap.MYSQL_ROOT_PASSWORD
if (-not $password) { throw '缺少 MYSQL_ROOT_PASSWORD' }
$sql = Join-Path $dir "fanghu-$stamp.sql"
cmd.exe /c "docker compose exec -T mysql mysqldump -uroot -p$password --single-transaction --routines --events fang_hu > `"$sql`""
cmd.exe /c "docker compose exec -T redis redis-cli --rdb - > `"$(Join-Path $dir "redis-$stamp.rdb")`""
docker run --rm -v fanghu_minio:/data -v "${dir}:/backup" alpine:3.20 tar czf "/backup/minio-$stamp.tar.gz" -C /data .
Write-Host "备份完成：$dir"
