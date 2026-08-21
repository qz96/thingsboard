#Requires -Version 5.1
<#
.SYNOPSIS
  ThingsBoard 单体镜像（tb-postgres）构建脚本（前后端融合包）。

  核心优化：前端源码未变更时，自动跳过 yarn install / ng build，复用已有前端产物。
  后端统一离线（-o）构建，利用本地 ~/.m2 全量缓存，避免海外仓库慢/卡死。

.PARAMETER Full
  强制重建前端 + 后端全量编译（等价于旧的完整构建）。
.PARAMETER Backend
  仅增量编译后端；前端未变更则跳过前端构建（默认行为）。
.PARAMETER Docker
  仅重新打包 Docker 镜像（复用已构建的 deb，不重新编译）。
.PARAMETER ForceUi
  强制重建前端（忽略变更检测）。
.PARAMETER SkipDocker
  跳过 Docker 构建步骤，只产出 jar/deb。
.PARAMETER DryRun
  只打印本次将执行的步骤，不真正执行构建。
#>
[CmdletBinding()]
param(
  [switch]$Full,
  [switch]$Backend,
  [switch]$Docker,
  [switch]$ForceUi,
  [switch]$SkipDocker,
  [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$Root = $PSScriptRoot
if (-not (Test-Path (Join-Path $Root 'pom.xml'))) {
  throw "未在项目根目录找到 pom.xml：$Root"
}

# ---------- 常量 ----------
$Threads        = 2
$StampFile      = Join-Path $Root 'ui-ngx\target\.frontend-hash'
$FrontendIndex  = Join-Path $Root 'ui-ngx\target\generated-resources\public\index.html'
$DockerCtx      = Join-Path $Root 'msa\tb\target\docker-postgres'
$DebFile        = Join-Path $DockerCtx 'thingsboard.deb'

# 前端构建影响面（改动任一即触发前端重建）
$UiPaths = @(
  'ui-ngx\src', 'ui-ngx\scss', 'ui-ngx\theme', 'ui-ngx\environments',
  'ui-ngx\esbuild', 'ui-ngx\patches', 'ui-ngx\vendor',
  'ui-ngx\package.json', 'ui-ngx\yarn.lock', 'ui-ngx\angular.json', 'ui-ngx\pom.xml',
  'ui-ngx\tsconfig.json', 'ui-ngx\tsconfig.app.json', 'ui-ngx\tailwind.config.js',
  'ui-ngx\proxy.conf.js', 'ui-ngx\generate-icon-metadata.js', 'ui-ngx\generate-types.js'
)

function Get-Sha256([string]$text) {
  $sha = [System.Security.Cryptography.SHA256]::Create()
  $bytes = [System.Text.Encoding]::UTF8.GetBytes($text)
  return ([System.BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-', '').ToLowerInvariant()
}

function Get-FrontendHash {
  $hashes = [System.Collections.Generic.List[string]]::new()
  foreach ($rel in $UiPaths) {
    $p = Join-Path $Root $rel
    if (-not (Test-Path $p)) { continue }
    $files = @()
    if ((Get-Item $p).PSIsContainer) { $files = Get-ChildItem $p -Recurse -File | ForEach-Object { $_.FullName } }
    else { $files = @($p) }
    foreach ($f in $files) {
      # 排除任何构建产物目录
      if ($f -match '\\node_modules\\|\\target\\|\\dist\\') { continue }
      $contentHash = (Get-FileHash -LiteralPath $f -Algorithm SHA256).Hash
      $hashes.Add(($f.Substring($Root.Length) + '|' + $contentHash))
    }
  }
  $hashes.Sort()
  return Get-Sha256 ($hashes -join ';')
}

function Write-Step([string]$msg) {
  Write-Host "`n========== $msg ==========" -ForegroundColor Cyan
}

function Invoke-Checked {
  param([string]$What, [scriptblock]$ScriptBlock)
  Write-Step $What
  & $ScriptBlock
  if ($LASTEXITCODE -ne 0) { throw "$What 失败（exit=$LASTEXITCODE）" }
}

# ---------- 0. 环境检查 ----------
foreach ($cmd in @('mvn', 'yarn')) {
  if (-not (Get-Command $cmd -ErrorAction SilentlyContinue)) { throw "未找到命令：$cmd，请检查 PATH" }
}

# ---------- 1. 前端变更检测 ----------
$script:UiHash = $null
$needFrontend = $false
$frontendOk = Test-Path $FrontendIndex

if ($Full -or $ForceUi) {
  $needFrontend = $true
  Write-Host '[前端] 强制重建（-Full/-ForceUi）'
} elseif (-not $frontendOk) {
  $needFrontend = $true
  Write-Host '[前端] 构建产物缺失，需构建前端'
} else {
  $script:UiHash = Get-FrontendHash
  if (Test-Path $StampFile) {
    $old = (Get-Content $StampFile -Raw).Trim()
    if ($old -ne $script:UiHash) { $needFrontend = $true; Write-Host '[前端] 源码有变更，需重建前端' }
    else { Write-Host '[前端] 源码无变更，跳过 yarn install / ng build' }
  } else {
    $needFrontend = $true
    Write-Host '[前端] 首次构建，需构建前端'
  }
}
$skipUi = if ($needFrontend) { 'false' } else { 'true' }

# ---------- 1.5 DryRun 预览 ----------
if ($DryRun) {
  Write-Host "`n[DRYRUN] 本次执行计划："
  if ($Docker) {
    Write-Host '  - 刷新 msa/tb 打包目录（复制 deb）'
  } else {
    if ($needFrontend) { Write-Host '  - 构建前端 ui-ngx（yarn install + ng build）' }
    else { Write-Host '  - [跳过] 前端构建（源码无变更）' }
    Write-Host "  - 构建后端 msa/tb -am (skip.ui=$skipUi, -T $Threads)"
  }
  if (-not $SkipDocker) {
    Write-Host '  - docker build tb-postgres:latest'
  } else {
    Write-Host '  - [跳过] Docker 构建'
  }
  exit 0
}

# ---------- 2. 构建 ----------
if (-not $Docker) {
  if ($needFrontend) {
      # 前端模块单独重建（clean 只作用于 ui-ngx，不影响后端 target）
      Invoke-Checked -What '构建前端 ui-ngx（yarn install + ng build）' -ScriptBlock {
        & mvn -o clean install -DskipTests -pl ui-ngx '-Dlicense.skip=true'
      }
      # 构建完成后写入基线哈希（-Full/-ForceUi 同样建立基线，否则后续增量构建会误判变更而重复构建前端）
      $script:UiHash = Get-FrontendHash
      $dir = Split-Path $StampFile -Parent
      if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
      Set-Content -LiteralPath $StampFile -Value $script:UiHash -NoNewline -Encoding ascii
    }

  # 后端（不带 clean，避免清掉 ui-ngx/target 前端产物；ui-ngx 模块在 reactor 内但 exec 被 -Dskip.ui 跳过）
  $backendArgs = @('-o', 'install', '-DskipTests', '-pl', 'msa/tb', '-am', '-Dlicense.skip=true', '-T', "$Threads")
  if ($skipUi -eq 'true') { $backendArgs += '-Dskip.ui=true' }
  Invoke-Checked -What '构建后端 msa/tb（含 application 依赖）' -ScriptBlock {
    & mvn @backendArgs
  }
}

# ---------- 3. Docker 镜像 ----------
if (-not $SkipDocker) {
  if ($Docker) {
    # 仅刷新 docker 构建目录（复制 deb + 脚本），不重新编译依赖
    Invoke-Checked -What '刷新 msa/tb 打包目录（复制 deb）' -ScriptBlock {
      & mvn -o package -DskipTests '-Ddockerfile.skip=true' -pl msa/tb
    }
  }
  if (-not (Test-Path $DebFile)) {
    throw "未找到 $DebFile ，请先运行后端构建（不要带 -SkipDocker）"
  }
  if (-not (docker images -q tb-base-postgres:local 2>$null)) {
    throw '未找到预烘焙基础镜像 tb-base-postgres:local，请先运行 .\build-base-postgres.ps1 构建一次'
  }
  Invoke-Checked -What 'docker build tb-postgres' -ScriptBlock {
    Push-Location $DockerCtx
    try { & docker build -t tb-postgres:latest . } finally { Pop-Location }
  }
  Write-Host "`n[完成] 镜像 tb-postgres:latest 已构建"
} else {
  Write-Host "`n[完成] 已按 -SkipDocker 跳过镜像构建"
}
