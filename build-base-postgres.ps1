#Requires -Version 5.1
<#
.SYNOPSIS
  一次性构建预烘焙基础镜像 tb-base-postgres:local（含 PostgreSQL 12）。
  供 tb-postgres 主镜像（Dockerfile FROM tb-base-postgres:local）离线快速构建。
  需先启动 Docker Desktop。
#>
$ErrorActionPreference = 'Stop'
$Root = $PSScriptRoot
$Ctx = Join-Path $Root 'msa\tb\docker-postgres\base-postgres'
$Dockerfile = Join-Path $Ctx 'Dockerfile'
if (-not (Test-Path $Dockerfile)) { throw "未找到基础镜像 Dockerfile：$Dockerfile" }

Write-Host "========== 构建基础镜像 tb-base-postgres:local ==========" -ForegroundColor Cyan
docker build -t tb-base-postgres:local -f $Dockerfile $Ctx
if ($LASTEXITCODE -ne 0) { throw '基础镜像构建失败' }
Write-Host "[完成] 基础镜像 tb-base-postgres:local 已构建"
