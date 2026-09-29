$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)

Write-Host '构建生产后端（backend）...'
& .\script\build-backend.ps1
Write-Host '构建生产前端...'
& .\script\build-frontend.ps1
Write-Host '生产构建完成。启动前端静态服务和后端 API...'
& docker compose up -d --build backend frontend
if ($LASTEXITCODE -ne 0) { throw '生产服务启动失败' }
Write-Host '生产服务已启动：前端 http://localhost/，后端 API 由 Nginx 反向代理。'
Write-Host '说明：根目录 RuoYi Maven 工程仅用于源码开发，不属于生产发布链路。'
