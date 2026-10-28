# 开源物联网平台「设备组织与区域层级」设计调研

> 调研时间：2026-10-27。Star 数取自 GitHub API 实时查询，非记忆值。
> 目的：为「租户管理员按区域查看设备」选择建模方向提供外部参照。

## 1. 样本与定位

| 项目 | Star | 语言 | 定位 | 与本次需求的相关性 |
|---|---|---|---|---|
| thingsboard/thingsboard | 22,609 | Java | 全功能 IoT 平台（设备管理+规则引擎+可视化） | ★★★★★ 现用平台 |
| emqx/emqx | 16,861 | Erlang | MQTT Broker（接入层） | ★★ 只做接入，不管设备目录 |
| traccar/traccar | 7,935 | Java | GPS 车联网平台（设备+轨迹+分组） | ★★★ 有设备分组与权限模型 |
| jetlinks/jetlinks-community | 6,665 | Java | 响应式企业级 IoT 平台（物模型+多协议） | ★★★★ 中文生态，数据权限设计完整 |
| apache/iotdb | 6,424 | Java | 时序数据库（树形路径模型） | ★★★★ 路径命名空间范式 |
| dgiot/dgiot | 4,860 | Erlang | 工业 IoT 平台（多协议+数字孪生） | ★★ 部署形态参考 |
| absmach/magistrala | 2,656 | Go | IoT 框架（Workspace/Group/Device/Channel） | ★★★★ 分组+策略授权范式 |
| openremote/core | 1,936 | Java | 资产管理型 IoT 平台（资产树） | ★★★★★ 资产树范式最贴近 |
| edgexfoundry/edgex-go | 1,550 | Go | 边缘计算框架 | ★★ 边缘侧设备抽象 |
| IoTSharp/IoTSharp | 1,387 | C# | 轻量 IoT 平台 | ★★ 单体小规模参考 |
| sitewhere/sitewhere | 1,046 | Java | 设备管理（已归档，Site/Zone 模型） | ★★★ 站点/区域模型 |
| eclipse-ditto/ditto | 942 | Java | 数字孪生框架（Thing/Feature/Policy） | ★★★ 策略化权限 |
| eclipse-hono/hono | 503 | Java | 协议适配层 | ★ 接入层下沉 |

---

## 2. 整体架构与技术选型

### 2.1 ThingsBoard（现用）

- **架构**：单体/微服务双形态；Actor 模型承载实体生命周期（`TbActorMailbox`），规则引擎为 actor 链；传输层独立（MQTT/CoAP/HTTP/LwM2M/SNMP）。
- **选型**：Java 17 + Spring Boot 3 + JPA/Hibernate + PostgreSQL（+ Cassandra/Timescale 可选）+ Angular 前端 + Redis（缓存/队列可选）。
- **模块划分**：`common/data`（领域模型）、`dao`（持久化）、`application`（Web/Service）、`rule-engine`（组件库）、`transport/*`（协议）、`ui-ngx`（前端）、`msa/*`（可拆分服务）。
- **数据模型核心**：`Entity`（统一 id/type）+ `relation`（有类型的有向边）+ `attribute_kv`（实体属性，SERVER/SHARED/CLIENT 三作用域）+ `ts_kv`（遥测）。设备属于 Tenant，可分配 Customer；Asset 可自由组树。
- **取舍**：用**通用图模型**换表达力 —— 任何实体可挂任意类型关系，代价是"层级查询"必须自己遍历，且没有强约束（谁都能建环）。CE 版没有 Entity Group，分组只能靠 relation/attribute。

### 2.2 OpenRemote

- **架构**：Manager（核心）+ Agents（协议适配，每个 Agent 独立数据上下文）+ 前端 Web Components + 规则引擎（Groovy/JS/Flow）。
- **选型**：Java/Groovy + TypeScript + PostgreSQL + JWT/Keycloak。
- **数据模型**：`Asset` 是唯一核心实体，**根节点固定为 Tenant 资产**，资产可任意组树（城市→楼宇→楼层→传感器）；资产有可配置的 `AssetType` 与动态属性（attribute + meta）；Agent 也是资产，设备通过 `agent link` 绑定属性。
- **取舍**：用一个"资产树"统一了组织层级、设备、协议连接三件事 —— 极简心智模型，代价是类型系统靠配置、树很深时查询与权限继承都变重。

### 2.3 JetLinks

- **架构**：全响应式（WebFlux + Reactor + Netty/Vert.x + R2DBC），组件化（network/protocol/things/rule-engine/timeseries 各自独立组件）+ 管理模块（device-manager 等）。
- **选型**：Java 17 + Spring Boot 3.4 + PostgreSQL + TimescaleDB/TDengine + Redis + ElasticSearch（可选）。
- **数据模型**：产品（物模型）→ 设备实例；网络组件/协议包负责接入；支持**数据权限**（菜单/按钮/数据三维，可控制单条数据）。
- **取舍**：以"物模型"为中心（产品定义属性/功能/事件），设备是实例 —— 适合异构设备统一接入，代价是平台概念多、二次开发门槛高。

### 2.4 Magistrala（原 Mainflux）

- **架构**：事件驱动微服务，消息内核 FluxMQ；控制面（身份/授权/目录）统一到 Atom；Writer 服务负责落库。
- **选型**：Go + Docker/K8s + Timescale/Postgres。
- **数据模型**：Workspace（≈租户）→ Group（可嵌套的集合）→ Device / Channel；授权用 Action → Permission Block → Role → Assignment 链。
- **取舍**：极简五概念（user/device/channel/message/policy），分组只做"集合"不做语义；权限粒度可以细到单个 channel —— 代价是业务语义（区域、站点）要自己在 Group 上编码。

### 2.5 时序侧：IoTDB / TDengine

- **IoTDB**：树形路径 `root.区域.站点.设备.测点`，前缀查询天然表达层级；设备用标签（region/station/line）识别。
- **TDengine**：超级表 + 子表 + 标签；标签可达 128 个，标签值可以是树状字符串（`北京·朝阳·望京`），按标签过滤会先剪枝子表再扫描。
- **取舍**：把"区域"下沉到**存储命名空间/标签**，换极致查询性能与压缩率；代价是区域变更=改历史路径或标签（写扩散），且没有引用完整性。

---

## 3. 设备组织的 6 种范式

| 范式 | 代表 | 表达方式 | 解决的问题 | 主要代价 |
|---|---|---|---|---|
| **P1 通用图关系** | ThingsBoard | 实体 + 有类型边 | 任意实体互联（设备挂多点、跨类型关系） | 无约束、层级查询需自写遍历、无内建分组视图 |
| **P2 资产树（单亲）** | OpenRemote、SiteWhere(Site/Zone) | parent_id 单亲树 | 语义直白、可直接渲染树、权限可沿树继承 | 设备不能多归属；跨维度分组要建第二棵树 |
| **P3 分组容器 + 成员表** | Magistrala Group、Traccar Group | group 实体 + membership | 成员可增删、组可嵌套、ACL 挂在组上 | 多一层表；层级遍历要递归或存路径 |
| **P4 层级编码 / 物化路径** | 行政区划码、TDengine 树状 tag | 编码或 `path` 字符串 | 前缀查询一次搞定层级包含、写少读多、易缓存 | 改父级要级联重写；无 FK 约束 |
| **P5 属性 / 标签** | ThingsBoard attribute、Influx/TDengine tag | KV 或 tag | 零建模成本，规则链/仪表板可直接用 | 弱类型、过滤效率取决于索引、易被绕过 |
| **P6 时序命名空间** | IoTDB | 路径即 schema | 存储分区与聚合极快 | 只适合不可变的物理层级，管理面不友好 |

**观察到的共性**：凡是需要"管理面按组织查看"的平台，都保留**显式层级/成员记录**（P2 或 P3），并把 P4 的路径作为加速字段；P5/P6 只用于数据面（存储、标签、规则读取），不作唯一真相。这是关键结论。

---

## 4. 权限模型对比

| 平台 | 隔离边界 | 授权载体 | 特点 |
|---|---|---|---|
| ThingsBoard CE | Tenant | 角色（SYS_ADMIN/TENANT_ADMIN/CUSTOMER_USER）+ 实体归属 | 无行级权限；分组权限是 PE 能力 |
| OpenRemote | Realm | 用户-角色-资产（可继承） | 权限随资产树继承，天然支持"区域管理员" |
| Magistrala | Workspace | Action/Permission/Role/Assignment | 可细到单 channel 的读写发布订阅 |
| Ditto | Namespace | Policy（策略文档） | 权限是数据对象的一部分，可随对象复制 |
| JetLinks | 租户 | 菜单/按钮/数据三维数据权限 | 可控制单条数据操作权限 |

对本需求的含义：若要「片区管理员只看本片区」，必须把区域做成**可授权对象**（P2/P3），纯属性方案（P5）做不到。

---

## 5. 对当前需求的候选方案

| 方案 | 组合 | 查询性能 | 一致性 | 权限可挂载 | 改动成本 | 适用判断 |
|---|---|---|---|---|---|---|
| **A 纯属性** （现设计文档口径） | 设备 SERVER_SCOPE `regionCode` + regionPath | 中（key_dictionary 二次 join） | 弱（可直接改属性） | 否 | 最低 | 设备量小、只读展示、两周内上线 |
| **B 属性 + 成员表**（修订推荐） | `tb_region` 树 + `tb_region_device_rel` 成员表 + 属性投影 | 高（成员表索引 join） | 强（单一写入口 + FK） | 是（后续可加） | 中 | 需要审计/调拨历史、未来要做片区权限 |
| **C 资产树**（OpenRemote 式） | TB Asset 承载区域，设备建 relation | 中（relation 遍历） | 中 | 是（TB 实体权限） | 中 | 想完全复用 TB 原生实体与审计，但 CE 无分组视图、relation 维护成本高 |
| **D 时序标签下沉**（TDengine/IoTDB 式） | 区域写入遥测 tag / 测量路径 | 最高 | 弱（写扩散） | 否 | 高（要动存储方案） | 只适合超大设备量 + 以时序分析为主，管理面仍要另做 |

### 评分（5 分制，针对"租户内按区域看设备 + 未来可能做片区权限"）

| 维度 | A 纯属性 | **B 属性+成员表** | C 资产树 | D 时序标签 |
|---|---|---|---|---|
| 表达力 | 3 | 5 | 4 | 4 |
| 查询性能 | 3 | 5 | 3 | 5 |
| 一致性/可审计 | 2 | 5 | 3 | 2 |
| 权限可扩展 | 1 | 4 | 4 | 1 |
| 改动成本（反向） | 5 | 3 | 3 | 1 |
| **合计** | 14 | **22** | 17 | 13 |

---

## 6. 结论与对既有设计文档的修订建议

1. **方向不变**（方案 C：专用区域视图），但**归属载体要改**：从"只有设备属性"改为"**成员表为真相 + 属性为投影**"。
   - 理由：调研样本中所有可运维的组织视图都保留显式成员/层级记录（Magistrala Group、Traccar Group、OpenRemote 资产树），属性/tag 只服务数据面。纯属性方案一旦需要"调拨历史""区域权限""归属巡检回溯"就无解。
   - 代价可控：多一张表 + 一个写入口，业务代码量增加约 15%。
2. **保留 `regionCode` / `regionPath` 属性**，但明确定位为**只读投影**：给规则链分流告警、仪表板 alias 过滤使用；由归属接口在事务内双写，巡检任务对账。
3. **`path` 仍值得保留**（P4）：区域表存 `path`，把"含下级"变成前缀查询；成员表存 `region_code` 精确匹配。两者配合，既快又不依赖 `attribute_kv` 的无索引扫描。
4. **权限预留**：成员表存在后，后续做「片区管理员」只需在接口层把用户的可授权区域集合展开为 `region_code IN (...)`，无需改数据模型（对应 OpenRemote 的树继承、Magistrala 的组授权思路）。
5. **不建议**走 C（资产树）路线：TB CE 无 Entity Group，relation 的维护成本高于收益，且资产树与设备树两套实体容易漂移。

### 需要同步修订的文档

`docs/region-device-view-design.md` 需调整的点：
- §0 决策表「设备归属载体」改为成员表 + 属性投影；
- §7.2 增加 `tb_region_device_rel` 表定义；
- §8.1 查询实现改为 join 成员表；
- §10 风险表第 1 条（属性弱约束）降级，新增"成员表与属性投影漂移"的对账要求。

## 7. 参考

- GitHub API 实时 star：thingsboard 22,609 / emqx 16,861 / traccar 7,935 / jetlinks 6,665 / iotdb 6,424 / magistrala 2,656 / openremote-core 1,936 / edgex-go 1,550 / sitewhere 1,046 / ditto 942
- OpenRemote 资产与代理模型：https://www.openremote.io/product/assets/
- OpenRemote 资产树根为 Tenant、资产类型与属性 meta：https://forum.openremote.io/t/work-on-v3-controller/206/5
- TDengine 超级表与标签（含树状标签值）：https://tdengine.com/?p=8254
- IoTDB 时序数据模型（设备与测点层级）：https://iotdb.apache.org/UserGuide/latest/Background-knowledge/Navigating_Time_Series_Data.html
- JetLinks 架构与模块：https://github.com/jetlinks/jetlinks-community
- Magistrala（Workspace/Group/Device/Channel + 策略授权）：https://github.com/absmach/magistrala
- 本仓库自身实现证据：`dao/src/main/resources/sql/schema-views.sql`（`device_info_active_attribute_view`）、`common/data/.../EntityType.java`、`application/.../service/state/DefaultDeviceStateService.java`
