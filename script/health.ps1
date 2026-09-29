$ErrorActionPreference = 'Continue'
Set-Location (Split-Path $PSScriptRoot -Parent)
docker compose ps
$checks = @(
  @{Name='前端'; Url='http://localhost/'},
  @{Name='生产后端（backend）'; Url='http://localhost:8081/actuator/health'}
)
$failed = 0
foreach ($check in $checks) {
  try { $response = Invoke-WebRequest -Uri $check.Url -UseBasicParsing -TimeoutSec 5; Write-Host "$($check.Name): HTTP $($response.StatusCode)" }
  catch { Write-Host "$($check.Name): FAIL ($($_.Exception.Message))" -ForegroundColor Red; $failed++ }
}
if ($failed -gt 0) { exit 1 }
