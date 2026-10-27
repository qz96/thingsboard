--
-- Copyright © 2016-2025 The Thingsboard Authors
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

-- Android application management: apps and their APK versions.
-- File metadata (apk_url, preview_url, etc.) is hosted by the company shared
-- file service; this schema only keeps metadata. Stand-alone and idempotent.

CREATE TABLE IF NOT EXISTS tb_android_app (
    id uuid NOT NULL CONSTRAINT tb_android_app_pkey PRIMARY KEY,
    created_time bigint NOT NULL,
    updated_time bigint,
    tenant_id uuid NOT NULL,
    title varchar(255) NOT NULL,
    pkg_name varchar(255) NOT NULL,
    description varchar(1024),
    icon_url varchar(2048),
    icon_file_name varchar(255),
    status varchar(32) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT tb_android_app_tenant_pkg_unq_key UNIQUE (tenant_id, pkg_name)
);

COMMENT ON TABLE tb_android_app IS '安卓应用管理-应用主表';
COMMENT ON COLUMN tb_android_app.id IS '主键 UUID';
COMMENT ON COLUMN tb_android_app.created_time IS '创建时间（epoch 毫秒）';
COMMENT ON COLUMN tb_android_app.updated_time IS '更新时间（epoch 毫秒）';
COMMENT ON COLUMN tb_android_app.tenant_id IS '租户 ID';
COMMENT ON COLUMN tb_android_app.title IS '应用名称';
COMMENT ON COLUMN tb_android_app.pkg_name IS '安卓包名（如 com.lihero.iot），租户内唯一';
COMMENT ON COLUMN tb_android_app.description IS '应用描述';
COMMENT ON COLUMN tb_android_app.icon_url IS '应用图标 URL（公司公用上传接口返回）';
COMMENT ON COLUMN tb_android_app.icon_file_name IS '应用图标原始文件名';
COMMENT ON COLUMN tb_android_app.status IS '应用状态：ACTIVE-启用 / DISABLED-停用';

CREATE TABLE IF NOT EXISTS tb_android_app_version (
    id uuid NOT NULL CONSTRAINT tb_android_app_version_pkey PRIMARY KEY,
    created_time bigint NOT NULL,
    updated_time bigint,
    tenant_id uuid NOT NULL,
    app_id uuid NOT NULL,
    version_name varchar(255) NOT NULL,
    version_code int NOT NULL,
    release_notes varchar,
    apk_url varchar(2048),
    apk_file_name varchar(255),
    apk_size bigint,
    apk_md5 varchar(64),
    preview_url varchar(2048),
    force_update boolean NOT NULL DEFAULT false,
    status varchar(32) NOT NULL DEFAULT 'DRAFT',
    CONSTRAINT fk_android_app_version_app_id FOREIGN KEY (app_id) REFERENCES tb_android_app(id) ON DELETE CASCADE,
    CONSTRAINT tb_android_app_version_app_code_unq_key UNIQUE (app_id, version_code)
);

COMMENT ON TABLE tb_android_app_version IS '安卓应用管理-版本表（APK 文件本体存于公司公用文件服务，本表只存元数据）';
COMMENT ON COLUMN tb_android_app_version.id IS '主键 UUID';
COMMENT ON COLUMN tb_android_app_version.created_time IS '创建时间（epoch 毫秒）';
COMMENT ON COLUMN tb_android_app_version.updated_time IS '更新时间（epoch 毫秒）';
COMMENT ON COLUMN tb_android_app_version.tenant_id IS '租户 ID';
COMMENT ON COLUMN tb_android_app_version.app_id IS '所属应用 ID，引用 tb_android_app.id，级联删除';
COMMENT ON COLUMN tb_android_app_version.version_name IS '版本名称（如 1.2.3）';
COMMENT ON COLUMN tb_android_app_version.version_code IS '版本号（整数递增），同应用内唯一';
COMMENT ON COLUMN tb_android_app_version.release_notes IS '发布说明（支持长文本）';
COMMENT ON COLUMN tb_android_app_version.apk_url IS 'APK 下载地址（公司公用上传接口返回的外链）';
COMMENT ON COLUMN tb_android_app_version.apk_file_name IS 'APK 原始文件名';
COMMENT ON COLUMN tb_android_app_version.apk_size IS 'APK 文件大小（字节）';
COMMENT ON COLUMN tb_android_app_version.apk_md5 IS 'APK 文件 MD5 校验值';
COMMENT ON COLUMN tb_android_app_version.preview_url IS '附件预览地址（公司公用预览接口返回）';
COMMENT ON COLUMN tb_android_app_version.force_update IS '是否强制更新：true-强更 / false-弱更（默认）';
COMMENT ON COLUMN tb_android_app_version.status IS '版本状态：DRAFT-草稿 / PUBLISHED-已发布 / RETRACTED-已下架';

CREATE INDEX IF NOT EXISTS idx_tb_android_app_tenant_created_time ON tb_android_app(tenant_id, created_time DESC);

CREATE INDEX IF NOT EXISTS idx_tb_android_app_version_app_created_time ON tb_android_app_version(app_id, created_time DESC);

-- At most one PUBLISHED version per app; enforced by service layer, backed up here;
CREATE UNIQUE INDEX IF NOT EXISTS uq_tb_android_app_version_published
    ON tb_android_app_version(app_id) WHERE status = 'PUBLISHED';
