# 排水平台 → 物联网平台 免密跳转 账号开通对接文档

> 版本：v2.2（账号与角色模型调整；多管理员规则；接口章节细化到字段级）
> 更新：2026-10-28  
> 面向：排水平台（智慧水务）对接方  
> 物联网平台侧实现：`IoTBridgeController`、`IoTAssertionVerifier`、`IoTProvisionService`

---

## 0. 本次变更摘要（对接方需要做什么）

| # | 变更                                                       | 对接方动作                                        |
| - | -------------------------------------------------------- | -------------------------------------------- |
| 1 | 租户粒度从「一个水务账号一个租户」改为「**一个项目（projectId）一个租户**」             | 断言中新增 `projectId`、`projectName`              |
| 2 | 角色不再一律开通为区域管理员，改为按角色映射：**项目管理员 → 区域管理员；其他账号 → 区域运维（只读）** | 断言中新增 `roles` 数组                             |
| 3 | 账号登录名与真实邮箱解耦                                             | 无需动作（物联网平台内部生成），但需知悉 `profile.login` 不再作为登录名 |
| 4 | 同一项目**可有多名区域管理员**（与 `roles` 一致），每个账号只由自己的断言决定角色 | `roles` 需准确反映该用户当前是否为项目管理员 |

**兼容性说明**：断言缺少 `projectId` 或 `roles` 将被**拒绝**（返回 `400 / errorCode 31`），不再按旧逻辑静默开通账号。上线前请完成断言升级。

### 0.1 如果你已经开始对接（v2.0/v2.1 → v2.2 影响说明）

本次只改了一条**业务规则**：同一项目从「只能有一个区域管理员」改为「**可以有多名区域管理员**」。接口、字段、签名、错误码、账号登录名、租户粒度**全部不变**。

| 你目前的做法 | 是否需要调整 | 说明 |
|---|---|---|
| 断言里只有一个账号带 `PROJECT_ADMIN` | **不用改** | 行为与之前一致 |
| 断言里多个账号带 `PROJECT_ADMIN` | **不用改**，但需确认业务预期 | 现在这些人都会拿到 `TENANT_ADMIN` 与 `scope=TENANT_ADMIN`（之前只有其中一人） |
| 断言里带了 `primaryAdmin` 字段（v2.0 可选字段） | **不用改** | 该字段现在被**忽略**，不会报错；可以保留或删除 |
| 前端依赖"只有一个人能进管理视图" | 需要复核 | 若按 `scope` 判断，逻辑仍然成立；若把"唯一负责人"写进了文案/流程，请同步调整 |
| 依赖"某人登录会把其他人降级"的行为 | **需要改** | 不会再有跨账号副作用，每个账号只影响自己 |
| 已按 v2.0 建过测试账号/租户 | **不用迁移** | 租户与账号标识不变；角色在下一次跳转登录时自动校正 |

**不需要重做的部分**（确认无变化）：RSA 密钥与签名方式、`iss`/`aud`、`jti` 防重放、`projectId` 租户幂等键、登录名生成规则、所有错误码与响应结构。

**联调用例变化**：v2.0 文档里的"管理员转让"用例（原用例 10/11）作废，改为「多管理员并存」「单个管理员撤销」「最后管理员保护」三条，见 §7.2 用例 10~13。

**需要你确认的一点**：如果业务上"每个项目只有一个负责人"是硬要求，请在排水平台侧保证同一时刻只有一个人拥有 `PROJECT_ADMIN` —— 物联网平台不再做仲裁，`roles` 怎么写就怎么生效。

---

## 1. 参与方与职责

| 参与方   | 职责                                                                              |
| ----- | ------------------------------------------------------------------------------- |
| 排水平台  | ① 生成并签署断言（RS256）② 提供验签公钥 ③ 保证 `projectId`/`roles` 准确 ④ 调用 exchange/provision 接口 |
| 物联网平台 | ① 验签与防重放 ② 幂等开通租户与账号 ③ 角色同步与审计 ④ 返回令牌或账号信息                                      |

---

## 2. 接口清单

### 2.0 通用约定

| 项 | 约定 |
|---|---|
| Base URL | 物联网平台地址，例如 `https://iot.example.com`（内网可为 `http://10.x.x.x:9090`） |
| 方法 | 两个接口均为 `POST` |
| Content-Type | `application/json;charset=UTF-8` |
| 认证 | 两个接口都在 `/api/noauth/**` 下，**不需要** Authorization 头，靠断言签名自证身份 |
| 幂等性 | 同一 `(sub, projectId)` 重复调用不会重复建租户/账号；但**同一断言（相同 `jti`）不能重复使用** |
| 超时建议 | 单次调用超时 ≥ 5 秒（首次调用要建租户 + 账号 + 凭据） |
| 响应编码 | UTF-8 JSON |

> `exchange` 与 `provision` 的**入参完全一致**，区别只在返回：`exchange` 返回令牌（可直接登录），`provision` 只返回账号信息（不签发令牌，适合联调与对账）。

---

### 2.1 凭证交换 `POST /api/noauth/iot/exchange`

**用途**：排水平台点击「进入物联网平台」时调用。校验断言 → 幂等开通租户/账号 → 签发令牌。拿到令牌后由排水平台跳转到物联网平台并注入令牌，实现免密登录。

**请求**

```http
POST /api/noauth/iot/exchange HTTP/1.1
Host: iot.example.com
Content-Type: application/json;charset=UTF-8

{
  "assertion": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `assertion` | string | 是 | 排水平台签发的 JWT，见 §3。长度通常 800~1500 字符 |

**curl 示例**

```bash
ASSERTION="$(your-signer)"
curl -sS -X POST 'https://iot.example.com/api/noauth/iot/exchange' \
  -H 'Content-Type: application/json' \
  -d "{\"assertion\":\"${ASSERTION}\"}"
```

**成功响应 `200`**

```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIuLi4ifQ.xxx",
  "refreshToken": "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIuLi4ifQ.yyy",
  "scope": "TENANT_VIEWER"
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `token` | string | **访问令牌**（JWT）。调用物联网平台接口时放在请求头 `X-Authorization: Bearer {token}` |
| `refreshToken` | string | **刷新令牌**。访问令牌过期后用它换新令牌，见 §2.4 |
| `scope` | enum | 账号在物联网平台的**实际角色**：`TENANT_ADMIN`（区域管理员，可读写）/ `TENANT_VIEWER`（区域运维，只读）。排水平台可据此决定落地页 |

**副作用**（仅首次调用发生）

1. `projectId` 对应的租户不存在 → 创建租户（标题取 `projectName`，缺省 `profile.userName`）；
2. `(sub, projectId)` 对应的账号不存在 → 创建账号（登录名由物联网平台生成，见 §4.1）；
3. 按 §5 规则写入/校正角色；
4. 开通与角色变更写审计日志。

> 重复调用不会重复创建；想知道本次是否新建，用 `provision` 接口看 `created`。

**典型耗时**：首次（建租户 + 账号）约 200~800ms；幂等命中约 50~150ms。

---

### 2.2 账号信息查询 `POST /api/noauth/iot/provision`

**用途**：联调期验证断言、确认账号落到哪个租户/什么角色、排查「某用户是否已开通」。**不签发令牌**，不产生登录会话。

**请求**：与 `exchange` 完全相同（`{"assertion": "..."}`）。

**成功响应 `200`**

```json
{
  "userId": "71a29d60-d282-11f1-be75-eb1d56bacdb1",
  "tenantId": "3874fca0-d29f-11f1-85ed-f1d692e66929",
  "tenantTitle": "长沙暮云纳污区",
  "email": "sso-821101@p-430100-01.iot",
  "authority": "TENANT_ADMIN",
  "created": true
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `userId` | uuid | 物联网平台账号 ID |
| `tenantId` | uuid | 所属租户（= 项目）ID，同一 `projectId` 的所有账号共用 |
| `tenantTitle` | string | 租户显示名，来自断言 `projectName`；创建后不随后续断言变化（避免改名影响历史数据） |
| `email` | string | 物联网平台**登录名**（非真实邮箱），格式见 §4.1 |
| `authority` | enum | `TENANT_ADMIN` / `TENANT_VIEWER` |
| `created` | boolean | `true` = 本次新建；`false` = 已存在（幂等命中） |

**联调建议**：先用本接口验证开通结果，确认无误后再把 `exchange` 接入登录流程，避免调试期产生大量会话。

---

### 2.3 令牌如何使用

物联网平台**不使用 Cookie**，令牌放在请求头：

```http
GET /api/tenant/devices?pageSize=10&page=0 HTTP/1.1
Host: iot.example.com
X-Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...
```

接入形态二选一：

| 形态 | 做法 |
|---|---|
| 页面跳转（同域/子域） | 排水平台把 `token`、`refreshToken` 写入约定的 localStorage key；或通过一次性 URL 参数传给物联网平台前端，由其落盘后清除 URL |
| 后端代理 / iframe | 排水平台后端持有令牌并代理调用物联网平台接口，不把令牌暴露给浏览器 |

`scope` 决定落地页：

| scope | 建议落地页 |
|---|---|
| `TENANT_ADMIN` | 物联网平台首页（管理视图，可增删改） |
| `TENANT_VIEWER` | 设备 / 仪表板只读视图 |

---

### 2.4 令牌续期 `POST /api/auth/token`

| 令牌 | 默认有效期 | 配置项 |
|---|---|---|
| 访问令牌 `token` | **2.5 小时**（9000 秒） | `JWT_TOKEN_EXPIRATION_TIME` |
| 刷新令牌 `refreshToken` | **7 天**（604800 秒） | `JWT_REFRESH_TOKEN_EXPIRATION_TIME` |

```http
POST /api/auth/token HTTP/1.1
Content-Type: application/json

{ "refreshToken": "eyJhbGciOiJIUzUxMiJ9..." }
```

成功响应与 `exchange` 相同（`token` / `refreshToken` / `scope`），旧令牌随即失效。

| 场景 | 处理方式 |
|---|---|
| 访问令牌过期（`401` + `errorCode=11` `JWT_TOKEN_EXPIRED`） | 用 `refreshToken` 调本接口换新令牌，重试原请求 |
| 刷新令牌也过期 | 让用户重新从排水平台发起一次跳转（新的断言走 `exchange`） |

> 不要用同一断言反复调 `exchange` 来「续期」：`jti` 一次性，第二次必然 `401 Assertion already used`。续期只走本接口。

---

### 2.5 调用时序

**首次跳转**

```
排水平台                                     物联网平台
   |  1. 生成断言(含 projectId / roles)          |
   |------------------------------------------->| POST /api/noauth/iot/exchange
   |                                            | 验签 → 建租户(若无) → 建账号(若无)
   |                                            | → 按断言定位角色(可多管理员) → 审计
   |  2. {token, refreshToken, scope}           |
   |<-------------------------------------------|
   |  3. 注入令牌并跳转                          |
   |------------------------------------------->| 进入首页(按 scope 决定视图)
```

**再次跳转（幂等）**

流程相同，但第 2 步不创建任何数据；若断言中的角色与库中不一致，按 §5 规则只调整**该账号自己**的角色（写入审计日志）。

---

## 3. 断言（JWT Assertion）规范

### 3.1 签名与算法

| 项    | 要求                                                                                   |
| ---- | ------------------------------------------------------------------------------------ |
| 算法   | **RS256**（RSA-SHA256）                                                                |
| 密钥长度 | RSA **≥ 2048** 位                                                                     |
| 公钥格式 | X.509 SubjectPublicKeyInfo，Base64 编码，**不含** PEM 头尾（`-----BEGIN PUBLIC KEY-----` 需去掉） |
| 传输   | 公钥离线交付，配置在物联网平台 `security.iot-bridge.rsa-public-key`                                 |
| 有效期  | `exp` 建议 ≤ 60 秒；物联网平台允许 60 秒时钟偏移                                                     |

### 3.2 Claims

| Claim                        | 层级            | 必填       | 说明                                                           |
| ---------------------------- | ------------- | -------- | ------------------------------------------------------------ |
| `iss`                        | 顶层            | ✅        | 签发方，须等于约定值（如 `water-platform`）                               |
| `aud`                        | 顶层            | ✅        | 受众，须等于约定值（如 `iot-platform`）                                  |
| `sub`                        | 顶层            | ✅        | **水务用户唯一 ID**（建议用用户主键，不是登录名）                                 |
| `iat`                        | 顶层            | ✅        | 签发时间                                                         |
| `exp`                        | 顶层            | ✅        | 过期时间                                                         |
| `jti`                        | 顶层            | ✅        | 一次性随机 ID，**重放会被拒绝**（物联网平台缓存 300 秒）                           |
| `projectId`                  | 顶层            | ✅ **新增** | 项目/区域唯一标识，**租户幂等键**                                          |
| `projectName`                | 顶层            | 建议       | 项目/区域显示名，租户标题来源                                              |
| `roles`                      | 顶层            | ✅ **新增** | 角色编码数组，见 §5                                                  |
| `profile.login`              | `profile`     | ✅        | 水务登录名（仅用于展示与排查，不再作为 IoT 登录名）                                 |
| `profile.userName`           | `profile`     | ✅        | 用户显示名 / 项目名（旧版用于租户标题）                                        |
| `profile.firstName`          | `profile`     | 建议       | 姓名                                                           |
| `profile.jobNumber`          | `profile`     | 可选       | 工号                                                           |
| `waterOrgId` / `waterAreaId` | 顶层或 `profile` | 可选       | 组织/区域 ID，落库到账号附加信息                                           |
| `act`                        | 顶层            | 可选       | 实际操作人（代操作场景）：`{sub, name, uname}`                            |

> `profile.*` 既支持嵌套在 `profile` 对象内，也支持平铺为 `profile.login` 形式的顶层 key。

### 3.3 示例 Payload

```json
{
  "iss": "water-platform",
  "aud": "iot-platform",
  "sub": "536850378017345536",
  "iat": 1793160000,
  "exp": 1793160060,
  "jti": "9f1c2b7e-2a44-4d5f-9b0a-1c7f3d2e6b11",
  "projectId": "P-430100-01",
  "projectName": "长沙暮云纳污区",
  "roles": ["PROJECT_ADMIN"],
  "profile": {
    "login": "csmypssup@lihero.com",
    "userName": "暮云排水",
    "firstName": "李四",
    "jobNumber": "83000000012"
  },
  "waterOrgId": "ORG-1001",
  "waterAreaId": "AREA-430100"
}
```

### 3.4 校验规则（物联网平台实际校验的内容）

| 校验项 | 规则 | 失败响应 |
|---|---|---|
| 签名算法 | 必须 **RS256**（其他算法直接拒绝） | `401 Invalid assertion` |
| 签名 | 用配置的 RSA 公钥验证 | `401 Invalid assertion` |
| `iss` | 必须**完全等于**配置值 | `401 Invalid assertion` |
| `aud` | 必须**等于**配置值（支持数组形式含该值） | `401 Invalid assertion` |
| `exp` | 必须存在且未过期，允许 **60 秒**时钟偏移 | `401 Invalid assertion`（服务端日志会显示 `JWT expired at ...`） |
| `jti` | 必须存在；**300 秒内只能使用一次**（进程内缓存） | `400 Assertion missing jti` / `401 Assertion already used` |
| `sub` / `projectId` | 业务必填（在验签之后校验） | `400` + 对应 message |
| `iat` / `nbf` | **不校验**（建议仍正确填写，便于排查） | — |

**时钟同步要求**：双方服务器时间偏差应小于 60 秒，否则会出现"刚签发就过期"或"签名有效但 `exp` 判定失败"。建议排水平台开启 NTP。

**`jti` 唯一性范围**：全平台维度，不区分用户/项目。建议用 UUID v4。

**关于多实例部署**：`jti` 防重放目前是**单实例进程内缓存**。若物联网平台以多副本部署，重放请求可能被另一副本接受，需改为共享缓存（Redis）。当前为单实例部署，无此问题。

---

## 4. 身份映射规则

| 维度  | 规则                                                                                 |
| --- | ---------------------------------------------------------------------------------- |
| 租户  | 按 `projectId` 唯一。首次见到该 `projectId` 时创建租户，标题取 `projectName`（缺省用 `profile.userName`） |
| 账号  | 按 `(sub, projectId)` 唯一。同一个人属于多个项目时，会得到**多个独立账号**（对应多个租户）                          |
| 登录名 | **由物联网平台生成**：`sso-{sub}@{projectCode}.iot`，与真实邮箱解耦；真实邮箱保存在账号附加信息中仅作展示              |
| 密码  | 开通时置为随机值并激活，实际登录只用免密跳转，不需要密码                                                       |
| 角色  | 见 §5                                                                               |

### 4.1 账号登录名生成规则

```
sso-{sub}@{projectId}.iot
```

生成规则（与实现一致）：

1. `sub` 与 `projectId` 都转小写，非 `[a-z0-9-]` 字符替换为 `-`，连续 `-` 合并、首尾 `-` 去除；
2. `projectId` 清洗后为空时用 `project` 兜底；
3. 示例：
   | `sub` | `projectId` | 生成登录名 |
   |---|---|---|
   | `821101` | `P-430100-01` | `sso-821101@p-430100-01.iot` |
   | `821101` | `P_430100_01`（下划线） | `sso-821101@p-430100-01.iot` |
   | `821101` | `项目A`（非 ASCII） | `sso-821101@project.iot`（已清洗为空） |

> ⚠️ 若 `projectId` 含中文或特殊字符，清洗后可能退化，导致**不同项目生成相同登录名**。请尽量使用 ASCII 编码（如行政区划码 / 业务编码）。

### 4.2 边界行为

| 场景 | 行为 |
|---|---|
| 排水平台修改了用户真实邮箱 | 不影响账号（登录名与邮箱解耦），`additionalInfo.waterEmail` 在下次登录时**不会**自动更新（当前只写一次） |
| 排水平台改了 `projectName` | 租户标题**不随之变化**（避免改名影响历史数据）；如需改名请在物联网平台侧操作 |
| 账号在物联网平台被删除，但映射表仍有记录 | 返回 `500 SSO mapping points to a missing account`，需运维清理 `tb_sso_account` 对应行 |
| 同一 `(sub, projectId)` 在排水平台被删除后重新创建 | 会复用原账号（`sub` + `projectId` 未变） |

> 为什么不用真实邮箱当登录名：水务侧改邮箱会导致 IoT 平台无法识别为同一人，从而重复建号并丢失历史配置。用 `sub` 作为身份锚点可避免此问题。

---

## 5. 角色映射与管理员同步规则

### 5.1 映射表

| `roles` 内容         | 物联网平台角色               | 权限                                  |
| ------------------ | --------------------- | ----------------------------------- |
| 含 `PROJECT_ADMIN`  | `TENANT_ADMIN`（区域管理员） | 该区域全部读写：设备、资产、仪表板、告警、设备类型、用户与区域配置   |
| 不含 `PROJECT_ADMIN` | `TENANT_VIEWER`（区域运维） | 只读：设备、资产、仪表板、告警、遥测与属性；**所有写操作 403** |

> 角色编码匹配规则（与实现一致）：
> - **大小写不敏感**（`PROJECT_ADMIN` / `project_admin` 均可）；
> - 自动去掉 `ROLE_` 前缀（`ROLE_PROJECT_ADMIN` 等价于 `PROJECT_ADMIN`）；
> - `roles` 为空数组时按 `TENANT_VIEWER` 处理。
> 若排水平台实际编码不是 `PROJECT_ADMIN`，请告知物联网平台侧调整常量。

### 5.2 多管理员同步规则

**同一项目可以有多个区域管理员**：每个 `PROJECT_ADMIN` 账号都会成为 `TENANT_ADMIN`，各自独立，互不影响。

每个账号的角色只由**自己的断言**决定，同步规则如下：

| 情形 | 行为 |
|---|---|
| 断言含 `PROJECT_ADMIN`，该账号当前不是管理员 | 升为 `TENANT_ADMIN`（不触碰其他管理员） |
| 断言不含 `PROJECT_ADMIN`，该账号当前是管理员，且租户内还有其他管理员 | 降为 `TENANT_VIEWER` |
| 断言不含 `PROJECT_ADMIN`，该账号是租户内**唯一**管理员 | **不降级**，保留 `TENANT_ADMIN` 并记录 WARN（防止区域无人可管） |
| 断言与库中角色一致 | 不变更 |

要点：

1. **无跨账号副作用**：A 登录不会改变 B 的角色，因此不会出现"两个管理员互相降级"。
2. **权限回收时机**：管理员在排水平台被撤销后，其物联网平台权限会在**本人下次跳转登录时**生效。若需立即回收，请在物联网平台侧手工调整（或后续启用全量对账方案）。
3. **管理员数量不设上限**，但每个账号必须由 `(sub, projectId)` 唯一确定，不会重复建号。

### 5.3 审计

每次角色变更都会写入物联网平台审计日志，包含：账号、旧角色、新角色、断言 `jti`、变更时间。

---

## 6. 错误响应

### 6.1 统一响应体

所有失败响应都是同一个结构（`Content-Type: application/json`）：

```json
{
  "status": 401,
  "message": "Invalid assertion",
  "errorCode": 10,
  "timestamp": 1793171435073
}
```

| 字段 | 说明 |
|---|---|
| `status` | HTTP 状态码（与响应行一致） |
| `message` | 可直接展示/记日志的错误描述，见下表 |
| `errorCode` | 业务错误码（数字），用于程序判断 |
| `timestamp` | 服务端毫秒时间戳 |

### 6.2 错误码与处理

| HTTP | errorCode | message（原文） | 触发条件 | 对接方处理 |
|---|---|---|---|---|
| 400 | 31 | `Missing assertion` | 请求体为空或缺 `assertion` 字段 | 检查请求体 |
| 400 | 31 | `Assertion subject (water user id) is missing` | 断言缺 `sub` | 断言必须带稳定的用户主键 |
| 400 | 31 | `Assertion projectId is missing` | 断言缺 `projectId` | **升级断言**（本版新增必填） |
| 400 | 31 | `Assertion profile.login is invalid` | `profile.login` 不是合法邮箱格式 | 传真实邮箱或去掉该字段 |
| 400 | 31 | `Assertion projectName or profile.userName is missing` | 两者都缺，无法确定租户标题 | 至少传一个 |
| 400 | 31 | `Assertion missing jti` | 断言缺 `jti` | 每条断言必须带唯一 `jti` |
| 400 | 31 | `Account [xxx] belongs to another tenant` | 登录名已存在但属于其他租户（历史脏数据） | 联系物联网平台排查映射表 |
| 401 | 10 | `Invalid assertion` | 验签失败：公钥不符、算法不是 RS256、`iss`/`aud` 不匹配、结构损坏 | 核对公钥、算法与 `iss`/`aud` |
| 401 | 10 | `Assertion already used` | 同一 `jti` 被第二次使用 | 每次请求生成新 `jti`，失败后重新签发 |
| 401 | 10 | `Invalid assertion`（日志为 `JWT expired at ...`） | `exp` 已过期（含 60 秒时钟偏移容忍） | 缩短「签发 → 调用」间隔，检查双方时钟 |
| 403 | 20 | `IoT bridge is disabled` | 物联网平台侧桥接开关关闭 | 运维开启 `security.iot-bridge.enabled` |
| 500 | 2 | `SSO mapping points to a missing account [uuid]` | 映射表指向的账号已被删除 | 联系物联网平台清理映射行后重试 |
| 500 | 2 | `Couldn't send user activation email` | 仅出现在管理后台手工建号；SSO 开通不使用邮件 | 与本对接无关 |

### 6.3 重试策略

| 情况 | 是否可重试 | 做法 |
|---|---|---|
| `400 / 401`（断言类错误） | **不可原地重试** | `jti` 已被消费；必须重新签发断言再调 |
| `403`（桥接关闭） | 不可重试 | 等运维恢复 |
| `5xx` / 网络超时 | 可重试 | **重新签发断言**后重试（不要复用旧断言），建议最多 2 次 |

---

## 7. 联调步骤与验收清单

### 7.1 准备（运维）

| # | 事项 | 说明 |
|---|---|---|
| 1 | 排水平台生成 RSA 密钥对（≥2048 位），提供**公钥** | Base64 X.509 SubjectPublicKeyInfo，去掉 PEM 头尾 |
| 2 | 物联网平台配置公钥与 `iss`/`aud` | 改 `thingsboard.yml` 的 `security.iot-bridge.*`（见 §10），重启生效 |
| 3 | 双方确认 `iss` / `aud` 常量 | 默认 `water-platform` / `iot-platform` |
| 4 | 确认测试项目 `projectId` 与两名测试账号 | 一名给 `PROJECT_ADMIN`，一名不给 |

### 7.2 用例（按顺序执行）

| # | 用例 | 请求 | 期望结果 |
|---|---|---|---|
| 1 | 管理员首次开通 | `provision`，roles=[PROJECT_ADMIN] | `200`，`authority=TENANT_ADMIN`，`created=true` |
| 2 | 同项目运维首次开通 | `provision`，roles=[] | `200`，`authority=TENANT_VIEWER`，`tenantId` 与用例 1 **相同** |
| 3 | 幂等验证 | 同用例 1 重新签发断言再调 | `200`，`created=false`，租户与账号未新增 |
| 4 | 防重放 | 把用例 1 的断言**原样**再发一次 | `401`，`errorCode=10`，`message=Assertion already used` |
| 5 | 管理员取令牌 | `exchange`，roles=[PROJECT_ADMIN] | `200`，`scope=TENANT_ADMIN` |
| 6 | 运维取令牌 | `exchange`，roles=[] | `200`，`scope=TENANT_VIEWER` |
| 7 | 只读权限生效 | 用用例 6 的 `token` 调 `GET /api/tenant/devices?pageSize=5&page=0` | `200` |
| 8 | 只读不可写 | 用用例 6 的 `token` 调 `POST /api/device` | `403` |
| 9 | 令牌续期 | `POST /api/auth/token`，body 带 `refreshToken` | `200`，返回新 `token`；旧 `token` 调用返回 `401` |
| 10 | 多管理员并存 | 第二个 `PROJECT_ADMIN` 账号调 `provision` | `200`，`authority=TENANT_ADMIN`，前一个管理员**不受影响** |
| 11 | 多管理员取令牌 | 两个管理员分别 `exchange` | 均返回 `scope=TENANT_ADMIN` |
| 12 | 单个管理员撤销 | 其中一个管理员断言去掉 PROJECT_ADMIN 后调 `provision` | `authority=TENANT_VIEWER`（因为还有其他管理员） |
| 13 | 最后管理员保护 | 仅剩的管理员断言去掉 PROJECT_ADMIN 后调 `provision` | 仍为 `authority=TENANT_ADMIN`（日志 WARN），避免区域无人可管 |
| 14 | 缺 projectId | 断言不带 `projectId` | `400`，`errorCode=31`，`message=Assertion projectId is missing` |
| 15 | 错误签名 | 用另一把私钥签名 | `401`，`errorCode=10`，`message=Invalid assertion` |

### 7.3 排查指引

| 现象 | 优先检查 |
|---|---|
| 全部断言都 `Invalid assertion` | ① 公钥是否被正确加载（注意 §10 的硬编码陷阱）② 算法是否 RS256 ③ `iss`/`aud` 是否与配置一致 ④ 服务端日志 `IoT bridge assertion verification failed: ...` 会给出具体原因 |
| 偶尔 `Assertion already used` | 断言在重试时被复用；每次重试都要重新签发 |
| `projectId is missing` | 排水平台断言版本未升级 |
| 同一项目出现多个租户 | 断言里 `projectId` 不一致（例如带了版本号/大小写不同）；租户按 `projectId` **严格相等**匹配 |
| 同一个人变成两个账号 | `sub` 不稳定（例如用了登录名/邮箱）；`sub` 必须是用户主键 |

---

## 8. 变更影响（物联网平台侧执行，供知悉）

1. 现有由旧逻辑开通的 **33 个空租户**（每个挂一个管理员账号、无任何设备/资产/仪表板）将被**删除**，随后按 `projectId` 重新开通。
2. 有数据的 7 个租户（`beijing`、`Tenant`、`shanxi`、`word`、`cs`、`ESP32_RTU设备`、`RTU设备管理`）均为手工创建、与排水平台账号无关，**不受影响**。
3. 上线后首次跳转即按新模型开通，历史账号（`xxx@lihero.com` 形式）不再使用，请对接方知悉登录名会变化。

---

## 9. 待确认事项（需排水平台回复）

| # | 问题                                                         | 影响                 |
| - | ---------------------------------------------------------- | ------------------ |
| 1 | `PROJECT_ADMIN` 的确切字符串是什么？是否还有其它等价编码（如 `project_admin`）？   | 决定角色判定             |
| 2 | `projectId` 的类型与稳定性：是数字 ID 还是业务编码？是否会被复用/更改？               | 决定租户幂等键            |
| 3 | 是否存在一个人同时属于多个项目的情况？频率多高？                                   | 决定账号数量策略           |
| 4 | `roles` 是否可能为空数组（例如新入职未分配角色）？                              | 空数组按「区域运维」处理，请确认   |
| 5 | 是否需要在跳转时携带业务上下文（如默认打开某台设备/某个仪表板）？                          | 决定是否增加 `ctx` claim |
| 6 | 断言签名公钥是否需要支持轮换（多把公钥并存）？                                    | 决定验签实现是否要升级        |
| 7 | 同一项目是否会出现多个 `PROJECT_ADMIN`？若会，是否需要「立即回收已撤销管理员的权限」（需断言携带全量管理员列表）？ | 决定是否升级为全量对账方案 |

---

## 10. 物联网平台部署注意（供双方运维知悉）

`thingsboard.yml` 中 `security.iot-bridge.rsaPublicKey` 目前是**硬编码字面量**，不是环境变量占位符。更换公钥请用以下任一方式：

1. 直接修改 `thingsboard.yml` 后重启（推荐，改动可追溯）；
2. 以 `SPRING_APPLICATION_JSON` 注入（优先级高于 yml），形如：
   ```json
   {"security":{"iot-bridge":{"enabled":true,"rsaPublicKey":"<Base64>","issuer":"water-platform","audience":"iot-platform"}}}
   ```

注意：环境变量 `SECURITY_IOT_BRIDGE_RSAPUBLICKEY` **不会**覆盖该字面量（已实测），不要依赖它。
