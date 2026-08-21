#!/usr/bin/env bash
#
# ThingsBoard 单体镜像（tb-postgres）构建脚本（前后端融合包）
# 与 build-monolith.ps1 逻辑一致；用于 Linux / CI。
#
# 用法:
#   ./build-monolith.sh            # 增量：前端未变更则跳过前端构建，然后后端 + 镜像
#   ./build-monolith.sh -Full      # 强制重建前端 + 后端全量
#   ./build-monolith.sh -Backend   # 仅后端增量
#   ./build-monolith.sh -Docker    # 仅重打镜像（复用已构建 deb）
#   ./build-monolith.sh -ForceUi   # 强制重建前端
#   ./build-monolith.sh -SkipDocker# 只产出 jar/deb，不构建镜像
#   ./build-monolith.sh -DryRun    # 只打印本次执行计划，不真正构建
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

THREADS=2
STAMP_FILE="$ROOT/ui-ngx/target/.frontend-hash"
FRONTEND_INDEX="$ROOT/ui-ngx/target/generated-resources/public/index.html"
DOCKER_CTX="$ROOT/msa/tb/target/docker-postgres"
DEB_FILE="$DOCKER_CTX/thingsboard.deb"

FULL=false; BACKEND=false; DOCKER=false; FORCE_UI=false; SKIP_DOCKER=false; DRY_RUN=false
for arg in "$@"; do
  case "$arg" in
    -Full)       FULL=true ;;
    -Backend)    BACKEND=true ;;
    -Docker)     DOCKER=true ;;
    -ForceUi)    FORCE_UI=true ;;
    -SkipDocker) SKIP_DOCKER=true ;;
    -DryRun)     DRY_RUN=true ;;
    *) echo "未知参数: $arg"; exit 1 ;;
  esac
done

UI_PATHS=(
  "ui-ngx/src" "ui-ngx/scss" "ui-ngx/theme" "ui-ngx/environments"
  "ui-ngx/esbuild" "ui-ngx/patches" "ui-ngx/vendor"
  "ui-ngx/package.json" "ui-ngx/yarn.lock" "ui-ngx/angular.json" "ui-ngx/pom.xml"
  "ui-ngx/tsconfig.json" "ui-ngx/tsconfig.app.json" "ui-ngx/tailwind.config.js"
  "ui-ngx/proxy.conf.js" "ui-ngx/generate-icon-metadata.js" "ui-ngx/generate-types.js"
)

frontend_hash() {
  ( cd "$ROOT" && find "${UI_PATHS[@]}" -type f \
      -not -path "*/node_modules/*" -not -path "*/target/*" -not -path "*/dist/*" 2>/dev/null \
    | sort | xargs sha256sum | sha256sum | awk '{print $1}' )
}

step() { echo; echo "========== $1 =========="; }

for cmd in mvn yarn; do command -v "$cmd" >/dev/null 2>&1 || { echo "未找到命令: $cmd"; exit 1; }; done

UI_HASH=""
NEED_FRONTEND=false
if [ -f "$FRONTEND_INDEX" ]; then
  if [ "$FULL" = true ] || [ "$FORCE_UI" = true ]; then
    NEED_FRONTEND=true
    echo "[前端] 强制重建"
  else
    UI_HASH="$(frontend_hash)"
    if [ -f "$STAMP_FILE" ] && [ "$(cat "$STAMP_FILE")" = "$UI_HASH" ]; then
      echo "[前端] 源码无变更，跳过 yarn install / ng build"
    else
      NEED_FRONTEND=true
      echo "[前端] 源码有变更或首次构建，需重建前端"
    fi
  fi
else
  NEED_FRONTEND=true
  echo "[前端] 构建产物缺失，需构建前端"
fi
SKIP_UI=$([ "$NEED_FRONTEND" = true ] && echo false || echo true)

if [ "$DRY_RUN" = true ]; then
  echo
  echo "[DRYRUN] 本次执行计划："
  if [ "$DOCKER" = true ]; then
    echo "  - 刷新 msa/tb 打包目录（复制 deb）"
  else
    if [ "$NEED_FRONTEND" = true ]; then
      echo "  - 构建前端 ui-ngx（yarn install + ng build）"
    else
      echo "  - [跳过] 前端构建（源码无变更）"
    fi
    echo "  - 构建后端 msa/tb -am (skip.ui=$SKIP_UI, -T $THREADS)"
  fi
  if [ "$SKIP_DOCKER" = false ]; then
    echo "  - docker build tb-postgres:latest"
  else
    echo "  - [跳过] Docker 构建"
  fi
  exit 0
fi

if [ "$DOCKER" = false ]; then
  if [ "$NEED_FRONTEND" = true ]; then
    step "构建前端 ui-ngx（yarn install + ng build）"
    mvn -o clean install -DskipTests -pl ui-ngx -Dlicense.skip=true
    [ -n "$UI_HASH" ] && { mkdir -p "$(dirname "$STAMP_FILE")"; printf '%s' "$UI_HASH" > "$STAMP_FILE"; }
  fi

  step "构建后端 msa/tb（含 application 依赖）"
  MAVEN_OPTS="${MAVEN_OPTS:-}" mvn -o install -DskipTests -pl msa/tb -am -Dlicense.skip=true -T "$THREADS" $([ "$SKIP_UI" = true ] && echo "-Dskip.ui=true")
fi

if [ "$SKIP_DOCKER" = false ]; then
  if [ "$DOCKER" = true ]; then
    step "刷新 msa/tb 打包目录（复制 deb）"
    mvn -o package -DskipTests -Ddockerfile.skip=true -pl msa/tb
  fi
  [ -f "$DEB_FILE" ] || { echo "未找到 $DEB_FILE ，请先运行后端构建"; exit 1; }
  if [ -z "$(docker images -q tb-base-postgres:local 2>/dev/null)" ]; then
    echo "未找到预烘焙基础镜像 tb-base-postgres:local，请先运行 ./build-base-postgres.sh 构建一次"
    exit 1
  fi
  step "docker build tb-postgres"
  ( cd "$DOCKER_CTX" && docker build -t tb-postgres:latest . )
  echo "[完成] 镜像 tb-postgres:latest 已构建"
else
  echo "[完成] 已跳过镜像构建"
fi
