#
# Copyright © 2016-2025 The Thingsboard Authors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# =====================================================================
# 预烘焙基础镜像：tb-base-postgres:local
#
# 用途：把 tb-postgres 主 Dockerfile 中「联网安装 PostgreSQL 12」这一段
# （apt 源 + 密钥 + install）一次性烘焙进基础镜像，之后每次 docker build
# 全程零联网、层缓存命中。
#
# 构建（一次性）：
#   docker build -t tb-base-postgres:local -f msa/tb/docker-postgres/base-postgres/Dockerfile msa/tb/docker-postgres/base-postgres
# 或运行仓库根目录 build-base-postgres.ps1 / build-base-postgres.sh
#
# 回滚：改回 tb-postgres 主 Dockerfile 的 FROM thingsboard/openjdk17:bookworm-slim 即可。
# =====================================================================

FROM thingsboard/openjdk17:bookworm-slim

ENV PG_MAJOR 12
ENV PGDATA=/data/db
ENV PATH=$PATH:/usr/lib/postgresql/$PG_MAJOR/bin

# 预下载的 PostgreSQL APT 密钥（来自 https://www.postgresql.org/media/keys/ACCC4CF8.asc，
# 已固化到构建上下文，构建时不再联网获取）。
COPY ACCC4CF8.asc /tmp/ACCC4CF8.asc

# 一次性构建，全程走国内镜像，避免海外源超时卡死：
#   1) Debian 系统源 deb.debian.org / security.debian.org -> mirrors.aliyun.com
#   2) PostgreSQL PGDG 源 apt.postgresql.org             -> mirrors.aliyun.com/postgresql
RUN sed -i 's|http://deb.debian.org/debian|http://mirrors.aliyun.com/debian|g; s|http://security.debian.org/debian-security|http://mirrors.aliyun.com/debian-security|g' \
        /etc/apt/sources.list /etc/apt/sources.list.d/*.sources /etc/apt/sources.list.d/*.list 2>/dev/null || true \
    && apt-get update \
    && apt-get install -y --no-install-recommends gnupg2 \
    && echo "deb http://mirrors.aliyun.com/postgresql/repos/apt/ $(. /etc/os-release && echo -n $VERSION_CODENAME)-pgdg main" > /etc/apt/sources.list.d/pgdg.list \
    && apt-key add /tmp/ACCC4CF8.asc \
    && rm -f /tmp/ACCC4CF8.asc \
    && apt-get update \
    && apt-get install -y --no-install-recommends postgresql-${PG_MAJOR} \
    && rm -rf /var/lib/apt/lists/* \
    && update-rc.d postgresql disable \
    && apt-get purge -y --auto-remove
