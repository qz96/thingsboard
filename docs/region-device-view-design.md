# 平台设备总览 / 区域设备视图 设计文档（v2 简化版）

> 前提变更（2026-10-28）：**1 个租户 = 1 个区域**，租户即区域。
> 原始需求同步修正为「**平台管理员需要查看全部区域的设备**」，不再要求"租户内按子区域分组"。
> v1 的 `tb_region` 区域树 / `tb_region_member` / `tb_region_device_rel` / `regionPath` 全部作废，原因见附录 A。

## 0. 结论摘要

| 项 | 结论 |
|---|---|
| 新增数据表 | **0 张**（区域=租户，设备归属用 `device.tenant_id`） |
| 新增角色 | **1 个**：`TENANT_VIEWER`（区域人员只读）；其余复用原生 |
| 新增页面 | 1 个：平台设备总览（左区域/租户列表 + 右统计卡 + 设备表） |
| 改动 TB 核心 | 仅 `Authority` + `DefaultAccessControlService` 两处（为只读角色） |
| 平台管理员改设备 | 自定义接口带 `tenantId`（推荐）；或原生 login-as 切区域管理员 |

相比 v1，删掉了区域树建模、成员表、归属表、path 维护、巡检任务，工作量从 7 个阶段降到 3 个。

---

## 1. 设计目标

### 1.1 业务目标

1. 平台管理员一次看到**全部区域（租户）**的设备规模与健康度，可下钻到任一区域看设备明细。
2. 平台管理员可在总览页内直接新增 / 修改 / 删除某区域的设备（无需切换账号）。
3. 区域管理员（= 租户管理员）在自己的区域（租户）内拥有完整权限，行为与现状一致。
4. 区域下人员（如运维值班、外部单位）只能查看，不能改。

### 1.2 非目标（本期不做）

- 租户内再按片区/区县细分的层级（若将来需要，再引入区域树，见附录 B）。
- 跨区域设备的统一调拨（区域即租户，调拨等于跨租户迁移，语义上不做）。
- 区域级大屏与地图呈现。

---

## 2. 定义与边界

| 概念 | 本设计中的含义 |
|---|---|
| 区域 | 一个租户（Tenant）。名称、隔离边界、设备归属全部复用 TB 原生 |
| 区域管理员 | `TENANT_ADMIN`，该租户的最高权限账号 |
| 区域人员 | 该租户下的只读账号，新增 `TENANT_VIEWER` |
| 平台管理员 | `SYS_ADMIN`，跨区域查看与操作 |

边界：本模块只读 `device` / `alarm` / `attribute_kv` / `tenant` 四张表，另经 `saveDevice` 写入设备；不改动设备、告警、遥测模型。

---

## 3. 页面组织

```
平台设备总览 (/platform-devices)   ← 仅 SYS_ADMIN 可见
├── 左侧：区域列表（租户列表 + 每区域设备数/在线数/告警数徽标）
├── 右侧顶部：选中区域的统计卡（设备总数 / 在线 / 离线 / 未清除告警）
└── 右侧下部：设备表（名称、设备类型、状态、最近活跃；支持搜索/状态筛选/新增/编辑/删除）
```

交互要点：

1. 左侧默认选中第一个区域，URL 记 `?tenantId=xxx`，可分享。
2. 设备表「新增/编辑/删除」直接调本模块接口，落库到选中租户；删除需二次确认。
3. 设备行点击跳转原生设备详情（平台管理员跳转后因无租户上下文会 404，因此改为行内「查看详情」抽屉，复用设备属性接口）。
4. 区域管理员的界面**不变**，继续用原生设备页；本页面只对 SYS_ADMIN 出现菜单。

---

## 4. 接口设计

前缀 `/api/platform`，全部 `@PreAuthorize("hasAuthority('SYS_ADMIN')")`，`tenantId` 显式传入（不依赖 `getTenantId()`）。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/platform/regions` | 区域（租户）列表，含 `deviceCount` / `activeCount` / `alarmCount` |
| GET | `/api/platform/regions/{tenantId}/summary` | 该区域统计卡数据（含按严重级告警分布） |
| GET | `/api/platform/regions/{tenantId}/devices` | 分页设备列表，参数 `pageSize/page/textSearch/status(ALL,ACTIVE,OFFLINE,ALARM)` |
| POST | `/api/platform/regions/{tenantId}/devices` | 新增设备（请求体为 TB 原生 `Device`）。⚠️ 页面已去掉「新增设备」入口，接口保留待用 |
| POST | `/api/platform/regions/{tenantId}/login-as` | 取该区域（租户）管理员的 JWT，用于「切换为当前区域」 |
| PUT | `/api/platform/regions/{tenantId}/devices/{deviceId}` | 修改设备 |
| DELETE | `/api/platform/regions/{tenantId}/devices/{deviceId}` | 删除设备 |

### 4.1 实现要点

- 读列表：`deviceService.findDevicesByTenantId(tenantId, pageLink)`（`DeviceService:71`）与 `deviceService.findDeviceInfoById(tenantId, deviceId)`，DAO 层本来就接受 `tenantId`，无需切换身份。
- 在线数：`attribute_kv` 中 `attribute_type=2 AND attribute_key='active'`，与 TB 自身的 `device_info_active_attribute_view` 同口径。
- 告警统计：`alarmService.findAlarms(tenantId, query)`（`AlarmService:96`）或等价 SQL，条件 `cleared = false`。
- 写设备：`deviceService.saveDevice(device)`，`device.tenantId` 由路径参数注入；删除同理由 `deviceService.deleteDevice(deviceId)` 封装。
- 区域列表的计数用一条聚合 SQL（`device` group by `tenant_id` + `attribute_kv` join `active`），避免 N 次查询。

---

## 5. 权限设计

### 5.1 角色矩阵

| 角色 | 范围 | 查看设备 | 增/改/删设备 | 设备类型（Device Profile） | 用户管理 |
|---|---|---|---|---|---|
| `SYS_ADMIN` 平台管理员 | 全部区域 | ✓ 本模块页面 | ✓ 本模块接口（带 `tenantId`） | ✓ | ✓ |
| `TENANT_ADMIN` 区域管理员 | 本区域（租户） | ✓ 原生页面 | ✓ 原生页面 | ✓ | ✓ |
| `TENANT_VIEWER` 区域人员（**新增**） | 本区域（租户） | ✓ 原生页面 + 只读接口 | ✗ | 只读 | ✗ |
| `CUSTOMER_USER` 外部客户（保持原样） | 本租户内其所属客户 | ✓ | ✓（原生可写，已知限制） | 只读 | ✗ |

### 5.2 只读角色 `TENANT_VIEWER`

- 新增 `Authority.TENANT_VIEWER`，权限 checker 只放行 `READ / READ_ATTRIBUTES / READ_TELEMETRY`（可加 `READ_CREDENTIALS` 视需要），其余全部拒绝。
- 注册点：`DefaultAccessControlService` 构造注入第四个 `Permissions` bean。
- 前端：菜单树新增该角色的菜单（设备只读列表 + 仪表板），路由 `data.auth` 加入该角色，用户表单角色下拉放开。
- 这是**唯一**需要动 TB 核心的地方；`Authority` 与 `DefaultAccessControlService` 是版本升级的合并热点，需登记。

### 5.3 平台管理员改设备的两条路

| 路线 | 做法 | 取舍 |
|---|---|---|
| **推荐：自定义接口** | 本模块 `POST/PUT/DELETE` 带 `tenantId`，自己校验 SYS_ADMIN + 租户存在 | 不切换会话；但只能在本页面内操作，原生设备页仍不可用 |
| 备选：原生 login-as | `GET /api/user/{userId}/token` 切到该租户管理员（`security.user_token_access_enabled` 默认 true，前端 `authService.loginAsUser`） | 可进原生页面；但会**替换当前 JWT**，切回需重新登录 |

两者可并存：页面内改设备走前者，需要完整原生功能时提供「切换到区域管理员」按钮。

---

## 6. 数据关系

```
tenant（区域） 1 ── n device        ← 复用原生 device.tenant_id
tenant（区域） 1 ── n alarm         ← 复用原生 alarm.tenant_id
device 1 ── 1 active 属性            ← attribute_kv，TB 维护
```

无新增表、无属性投影、无一致性巡检。

---

## 7. 与现有模块的集成

| 集成点 | 方式 |
|---|---|
| 设备/告警/遥测 | 只读复用，不改模型 |
| 设备类型（Device Profile） | 平台管理员可直接调原生接口（SYS_ADMIN 已有该资源权限） |
| 用户管理 | 新增角色需放开下拉；`CUSTOMER_USER` 保持不变 |
| 菜单/路由/i18n | `menu.models.ts` 两处（`menuSectionMap` + `defaultUserMenuMap` 的 SYS_ADMIN 树）、路由 `data.auth`、locale 中英文 |
| 区域主数据（租户信息） | 复用原生租户管理页，本模块只读 |

---

## 8. 约束与风险

| # | 风险 | 应对 |
|---|---|---|
| 1 | 平台管理员跨租户聚合查询在大租户量下变慢 | 聚合 SQL 走 `device(tenant_id)` 索引；区域列表加 30s 缓存 |
| 2 | 平台管理员在本模块改设备，绕过了 TB 的实体级校验 | 接口内自行校验：租户存在、设备属于该租户、设备类型存在；写 `audit_log` |
| 3 | 删除设备的不可恢复性 | 沿用原生逻辑：`TbDeviceService.delete(device, user)`（物理删除 + `logEntityAction` 审计），前端二次确认；不做软删 |
| 4 | 新增 `Authority` 与 TB 升级冲突 | 改动集中在 2 个文件，登记升级清单 |
| 5 | 区域人员通过原生页面越权（若只加菜单不加校验） | `TENANT_VIEWER` 的 checker 覆盖 device/asset/alarm/telemetry 四类资源，且前端隐藏按钮 |
| 6 | `CUSTOMER_USER` 仍可写设备（原生限制） | 本期不处理；如需彻底只读，把外部客户也迁到 `TENANT_VIEWER`（需确认客户隔离如何表达） |

---

## 9. 实施计划

| 阶段 | 交付 | 验收 |
|---|---|---|
| S1 | 后端：区域聚合 + 统计 + 设备列表三个只读接口 | 与 SQL 直查比对一致（设备数/在线数/告警数） |
| S2 | 后端：设备增改删接口（带 tenantId）+ 审计日志 | 平台管理员在接口侧完成增改删，越权（非 SYS_ADMIN）返回 403 |
| S3 | 前端：平台设备总览页（区域列表 + 统计卡 + 设备表 + 增改删弹窗） | 菜单仅 SYS_ADMIN 可见；增改删后统计即时刷新 |
| S4 | `TENANT_VIEWER` 角色（后端 checker + 前端菜单/路由/用户表单/文案） | 该角色能看设备列表，所有写操作 403 |
| S5（按需） | 「切换到区域管理员」按钮（login-as） | 切换后能进原生设备页 |

## 10. 测试建议

1. 聚合口径：区域列表的设备数 = 该租户 `device` 行数；在线数 = `active` 属性为 true 的数量。
2. 越权：TENANT_ADMIN 调 `/api/platform/*` 应 403；SYS_ADMIN 传不存在/已删除的 tenantId 应 404。
3. 只读角色：`TENANT_VIEWER` 遍历设备/告警/遥测的写接口，全部 403；UI 无编辑按钮。
4. 写操作一致性：通过本模块新增的设备，能在原生设备页与设备详情中看到（同一租户）。
5. 性能：50 个区域、单区域 1 万设备时，区域列表 < 800ms，统计卡 < 300ms。

## 11. 已确认事项（2026-10-28）

| # | 决策 |
|---|---|
| 1 | 区域人员只读 → 新建 `TENANT_VIEWER` |
| 2 | 需要「切换到区域管理员」按钮（login-as），与页面内直接操作并存 |
| 3 | 删除设备沿用原生逻辑（物理删除 + 审计 + 二次确认），不做软删 |
| 4 | ~~区域列表不显示已停用租户~~ **取消**：TB CE 的 `tenant` 表没有状态字段，不引入额外约定，区域列表返回全部租户 |

### 11.1 关于「停用租户」

已核实 `tenant` 表无任何状态字段（`schema-entities.sql`：id / created_time / additional_info / tenant_profile_id / 地址 / version），
因此**不引入自定义约定**，区域列表返回全部租户。将来若 TB 或业务提供正式状态字段，再在区域列表 SQL 加一处过滤即可。

## 12. 进度

| 阶段 | 状态 |
|---|---|
| S1 只读接口 | ✅ 已完成（`PlatformDeviceController` 三个 GET + `PlatformRegionStatsService`），`common/data`、`dao`、`application` 编译通过 |
| S2 设备增改删 | ✅ 已完成（`POST/PUT/DELETE /api/platform/regions/{tenantId}/devices`），编译通过 |
| S3 前端页面 | ✅ 已完成（`/platform-devices`：区域列表 + 统计卡 + 设备表 + 增改删弹窗），已部署至容器 |
| S4 `TENANT_VIEWER` | ✅ 已完成并端到端验证（角色 + 权限 + 区域人员页面） |
| S5 login-as 按钮 | ✅ 已完成（`POST /api/platform/regions/{tenantId}/login-as` + 页面「切换为当前区域」按钮） |

### S1 交付清单

| 文件 | 说明 |
|---|---|
| `common/data/.../platform/RegionOverview.java` | 区域（租户）概览 DTO |
| `common/data/.../platform/RegionSummary.java` | 单区域统计 DTO（含告警严重级分布） |
| `common/data/.../platform/RegionDeviceInfo.java` | 区域设备行 DTO（含在线态、未清除告警数） |
| `dao/.../sql/platform/PlatformRegionStatsRepository.java` | 5 条 native SQL：区域列表、区域统计、告警严重级、设备分页、设备计数 |
| `dao/.../platform/PlatformRegionStatsService(+Impl).java` | 统计聚合与分页封装 |
| `application/.../controller/PlatformDeviceController.java` | S1 三个 GET；S2 三个写接口 |

### S3 前端交付清单

| 文件 | 说明 |
|---|---|
| `shared/models/platform.models.ts` | 区域/统计/设备行类型 + 状态枚举 |
| `core/http/platform-device.service.ts` | 6 个接口的封装 |
| `home/pages/platform/platform-devices.component.ts/html/scss` | 页面：左侧区域列表（带设备数、可搜索）+ 右侧统计卡 + 设备表（搜索/状态筛选/分页）+ 增改删 |
| `home/pages/platform/platform-device-dialog.component.ts/html/scss` | 新增/编辑弹窗（名称、类型、标签、访问令牌） |
| `home/pages/platform/platform-device-routing.module.ts` / `.module.ts` | 路由 `/platform-devices`，`auth: [SYS_ADMIN]` |
| `menu.models.ts` | `MenuId.platform_devices` + 菜单项 + **SYS_ADMIN 菜单树** |
| `home-pages.module.ts` | 注册 `PlatformDeviceModule` |
| `locale.constant-en_US.json` / `zh_CN.json` | `platform.*` 共 28 个键 |

注：设备类型只需填名称——`DeviceServiceImpl.doSaveDevice` 在 `deviceProfileId` 为空时会按 `type` 自动查找或创建设备类型，因此无需额外的 profile 下拉接口。

### S5 「切换为当前区域」实现

| 环节 | 做法 |
|---|---|
| 后端 | `POST /api/platform/regions/{tenantId}/login-as`：`userService.findTenantAdmins(tenantId, new PageLink(1,0))` 取首个租户管理员 → 校验凭据启用 → `tokenFactory.createTokenPair(new SecurityUser(admin, enabled, principal))` 返回 `JwtPair`。受 `security.user_token_access_enabled` 开关约束（默认 true）。**不能复用原生 `/api/user/{id}/token`**：它的 `checkUserId` 依赖当前用户租户，SYS_ADMIN 的租户为 null，必然 404 |
| 前端 | 页面按钮「切换为当前区域」→ 取 token → `authService.setUserFromJwtToken(token, refreshToken, true)` → `window.location.href = '/home'`（整页重载，保证菜单按新身份重算） |
| 菜单顺序 | `platform_devices` 调整到 SYS_ADMIN 树的 `home` 之后、`tenants` 之前 |
| 新增设备入口 | 页面移除；编辑/删除保留 |

### S2 实现要点

| 接口 | 行为 |
|---|---|
| `POST /regions/{tenantId}/devices` | 拒绝带 `id` 的请求体（防跨租户覆盖）；注入 `tenantId`；`TbDeviceService.save(device, accessToken, user)` → 自动建凭据 + 审计 |
| `PUT /regions/{tenantId}/devices/{deviceId}` | 先按 `tenantId + deviceId` 加载（不存在返回 404）；校验 body id 一致；**只覆盖 name/label/type**，客户绑定、profile、附加信息等保持原值；类型变更时清空 `deviceProfileId` 让其按新类型重新解析 |
| `DELETE /regions/{tenantId}/devices/{deviceId}` | 先加载校验归属；`TbDeviceService.delete(device, user)` → 物理删除 + `logEntityAction` 审计（与原逻辑一致） |

权限：三个写接口均 `@PreAuthorize("hasAuthority('SYS_ADMIN')")` + `checkTenantId(regionId, WRITE)`。

---

## 附录 A：v1 方案为何作废

v1 假设"一个租户内还有多个区域（市/区县/片区）"，因此设计了 `tb_region` 树 + `tb_region_member` + `tb_region_device_rel` + 设备 `regionCode/regionPath` 属性投影，并需要 path 级联重建、归属巡检、两个新角色。

前提确认为「1 租户 = 1 区域」后：

| v1 组件 | 作废原因 |
|---|---|
| `tb_region` 树 | 区域就是 `tenant` 表，重复建模 |
| `tb_region_member` | 区域管理员就是 `TENANT_ADMIN`，天然一对一 |
| `tb_region_device_rel` | 设备归属就是 `device.tenant_id` |
| `regionCode/regionPath` 属性 | 无子区域层级，无投影价值 |
| `RegionScopeResolver`、path 重建、巡检 | 无范围可算、无路径可重建 |
| `REGION_ADMIN` 角色 | 与 `TENANT_ADMIN` 完全重合 |

保留下来的：**平台侧跨区域只读聚合 + 平台侧设备增删改 + 只读角色**——即本文档的全部内容。

## 附录 B：将来若真要"租户内再分片区"

按 v1 的 `tb_region`（树 + `path`）+ `tb_region_device_rel`（`device_id` 主键）+ `tb_region_member`（多对多）方案实施，本文档的接口与页面可以平滑扩展为「区域下钻一层」，无需推翻。
