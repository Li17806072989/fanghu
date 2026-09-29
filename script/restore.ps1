param([Parameter(Mandatory=$true)][string]$SqlFile, [string]$MinioArchive)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if (-not (Test-Path $SqlFile)) { throw "找不到 SQL 备份：$SqlFile" }
$envMap = @{}
Get-Content '.env' | ForEach-Object { if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$') { $envMap[$matches[1]] = $matches[2].Trim().Trim('"') } }
$password = $envMap.MYSQL_ROOT_PASSWORD
if (-not $password) { throw '缺少 MYSQL_ROOT_PASSWORD' }
Write-Host '即将覆盖 fang_hu 数据库，按 Ctrl+C 取消。'
Read-Host '输入 RESTORE 确认' | ForEach-Object { if ($_ -ne 'RESTORE') { throw '已取消恢复' } }
Get-Content $SqlFile -Raw | docker compose exec -T mysql mysql -uroot "-p$password" fang_hu
if ($MinioArchive) { if (-not (Test-Path $MinioArchive)) { throw "找不到 MinIO 备份：$MinioArchive" }; docker run --rm -v fanghu_minio:/data -v "$(Split-Path (Resolve-Path $MinioArchive) -Parent):/backup" alpine:3.20 sh -c "rm -rf /data/* && tar xzf /backup/$(Split-Path $MinioArchive -Leaf) -C /data" }
Write-Host '恢复完成，请重启后端并执行 health.ps1。'
