#!/usr/bin/env bash
#
# 一次性构建预烘焙基础镜像 tb-base-postgres:local（含 PostgreSQL 12）。
# 供 tb-postgres 主镜像（Dockerfile FROM tb-base-postgres:local）离线快速构建。
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CTX="$ROOT/msa/tb/docker-postgres/base-postgres"
DOCKERFILE="$CTX/Dockerfile"

[ -f "$DOCKERFILE" ] || { echo "未找到基础镜像 Dockerfile: $DOCKERFILE"; exit 1; }

echo "========== 构建基础镜像 tb-base-postgres:local =========="
docker build -t tb-base-postgres:local -f "$DOCKERFILE" "$CTX"
echo "[完成] 基础镜像 tb-base-postgres:local 已构建"
