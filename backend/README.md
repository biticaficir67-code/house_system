# 二手房业务管理系统后端

Spring Boot 3.5、Java 17、Spring Security、MyBatis、MySQL 8.0+。所有 Java 文件保持在 `src/main/java/system`，业务模块内直接放 Controller/Service/Models，不增加 entity/dto/impl 等层级目录。

## 当前组内进度

已合并队员新增的自定义报表接口；兼容调整和新上传全量 SQL 的使用边界见 [队员报表合并说明](TEAM_REPORT_INTEGRATION.md)。

已按10月22日晚之后的组内讨论对齐当前 main 与最新演示底稿，见 [本轮后端对齐说明](TEAM_ALIGNMENT.md)。当前账号直接使用 sys_admin、broker 原字段，不再复制到 sys_user；登录步骤见 [组员登录联调](TEAM_LOGIN.md)。

新增经纪人申请与审核、四种账号状态、本人资料、员工操作日志、媒体封面排序、共同委托、待跟进/回访、预约交接及成交核实拒绝重提。详见 [表格需求实现说明](WORKFLOW_IMPLEMENTATION.md) 和 [第三步联调清单](INTEGRATION_CHECKLIST.md)。

当前组内以 house_system_latest.sql 为完整演示底稿，不额外执行历史 V001～V003。代码不自动执行数据库迁移，旧账号复制服务与 V004 已移除。

## 已实现

- Bearer Token、BCrypt 密码、退出/改密后撤销会话、登录失败限流；管理员查看全部业务，经纪人只操作本人所在门店且由本人负责的数据。
- 仅管理员 ADMIN、经纪人 BROKER 两种固定身份，每个账号选择一种；经纪人提交申请后由管理员审核，也保留管理员直接开通内部账号，菜单由权限生成，后端逐接口鉴权。
- 管理员/经纪人工作台：今日预约、在售房源、待核验房源、待核实成交、委托到期与未读提醒。
- 房源分页筛选、详情、录入编辑、负责人交接、核验、上下架、归档、状态时间线。
- JPEG/PNG/WebP/MP4 上传、内容签名校验、受权限保护的媒体读取与逻辑删除。
- 业主、委托关系、跟进；委托冲突检测、每日到期任务与已读提醒。
- 客户、标签、需求、跟进和按已填写条件解释匹配房源。
- 预约、改期、完成、取消、回访；记录执行经纪人，防止同时段重复预约。
- 成交登记、核实、修改和撤销；挂牌价和成交价分别保存，成交事务同步房源、客户和未完成预约。
- 房源事务提交后异步调用文案分析；价格参考由用户触发，保留输入快照、版本、历史和失败状态。
- 基础报表、日期/门店/角色范围过滤、CSV 导出；C 可通过 `ReportExtension` 接入复杂统计 SQL。
- Swagger/OpenAPI、统一错误和 requestId、跨域配置。

## 两类人员的职责

系统供内部人员使用。业主和客户是业务档案，由内部人员维护，不开设登录账号。经纪人可提交内部入职申请，审核通过才允许登录业务。门店是业务归属，不再设置门店管理人员角色。

| 业务 | 管理员 ADMIN | 经纪人 BROKER |
| --- | --- | --- |
| 工作台 | 全局待办、待核验房源、待核实成交 | 本人今日带看、待核验进度、到期委托 |
| 账号和门店 | 开通账号、启停、重置密码、管理门店 | 查看本店信息、修改本人密码 |
| 房源和媒体 | 全部资料、核验、分配与交接、上下架、归档 | 本人房源录入/编辑/媒体、核验通过后上下架、符合条件时归档 |
| 业主和委托 | 全部档案、委托、负责人调整、跟进和提醒 | 本人档案、委托、跟进和提醒 |
| 客户和需求 | 全部档案、负责人调整、需求与跟进 | 本人客户、需求、标签、房源匹配与跟进 |
| 预约和带看 | 全部预约、改期、执行记录、回访 | 本人预约、改期、执行记录、回访 |
| 成交 | 全部登记、核实、纠错、撤销 | 本人成交登记、核实前修改、核实前撤销 |
| AI 辅助 | 对全部可见房源使用文案分析与价格参考 | 对本人房源使用，保留结果与历史 |
| 报表和导出 | 全局统计，可筛选门店、日期、分组 | 个人范围统计和房源导出，不能查看他人业绩 |

原店长的核验与分配、财务的成交核实、内勤的账号和全局资料维护由管理员承担；日常录入、联系与回访由经纪人完成。核验、挂牌、成交登记、成交核实继续分步骤保留，避免单次录入直接成为已核实的报表数据。权限、状态流转和请求示例见 [API.md](API.md)。

## 数据库准备

当前协作基线是 `../database/house_system_latest.sql`，使用 MySQL 8.0+。它是会清空重建 house_system 的完整演示包，仅用于已确认可以覆盖的开发环境；已有要保留的数据时先备份并核对方案。后端不会自动导入或重建数据库。

已导入 latest 的库直接配置并启动；管理员使用 sys_admin、经纪人使用 broker，初始化、注册、审核与账号维护通过已有 ADMIN 角色行锁串行处理，不依赖 sys_bootstrap 或 sys_user。

历史数据库底稿与 backend 中 V001～V003 仅供查阅旧实现，不用于当前账号体系。旧版 auth_session 带 sys_user 外键，不能直接用于本版；字段与边界见 [TEAM_LOGIN.md](TEAM_LOGIN.md)。

最新底稿同时保留 broker.name/broker_name、旧账号列。后端在正常新增档案时兼容必填列、保存 BCrypt 摘要，改名时同步双字段；不修改组员数据库文件，也不在启动时执行结构变更。

## 配置与启动

在 IDEA 运行配置或本机环境设置：

| 变量 | 默认值/说明 |
| --- | --- |
| DB_URL | jdbc:mysql://127.0.0.1:3306/house_system?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai |
| DB_USERNAME | root |
| DB_PASSWORD | 空；按开发库实际密码设置 |
| BOOTSTRAP_KEY | 空表示关闭首次管理员初始化入口 |
| SERVER_PORT | 8080 |
| TOKEN_HOURS | 12 |
| CORS_ALLOWED_ORIGINS | localhost:5173、127.0.0.1:5173、127.0.0.1:5174 对应的 http 地址 |
| UPLOAD_DIRECTORY | ./uploads；部署时设持久化绝对路径 |
| AI_TEXT_BASE_URL | http://127.0.0.1:5001 |
| AI_PRICE_BASE_URL | http://127.0.0.1:8001 |
| REMINDER_CRON | 每天 09:00 扫描委托到期（服务器时区应设 Asia/Shanghai） |

从 `backend/` 执行：

```shell
mvn spring-boot:run
```

或在 IDEA 中运行 `system.HouseSystemApplication`。本地配置可放 `application-dev.yml`，该文件由 backend/.gitignore 排除。

最新底稿原表中管理员 admin、经纪人 broker01～broker10 的初始密码均为123456；实际已改密时以原表为准。旧明文密码成功核验后原字段升级为 BCrypt。已有管理员无需 bootstrap；只有业务表及 ADMIN 角色齐备、sys_admin 为空时才临时启用初始化，完成后清空密钥。

接口文档：`http://localhost:8080/swagger-ui/index.html`。OpenAPI JSON：`/v3/api-docs`。登录获取 token 后，在 Swagger 的 Authorize 输入 token，再调用受保护接口。完整字段与流程见 [API.md](API.md)。

## 协作与实现约定

- `system` 包是基础包，子模块 `system/system` 负责账号角色门店。
- SQL 参数通过 MyBatis 绑定；通用 `DataMapper/DataStore` 仅接受 Java 内部生成的 SQL，HTTP 请求不能提供表名、列名或排序 SQL。现有 `HouseMapper.xml` 保留原查询协议供组员参考。
- 两种身份及权限固定：ADMIN 为 ALL，BROKER 为 OWN（本店且负责人为本人）。角色只提供查询接口，移除新增角色、编辑权限和任意数据范围配置接口；服务端按身份确定实际权限，不信任数据库中遗留的自定义权限字符串。后续如需调整职责，统一修改 `security/Permissions.java` 并同步迁移脚本与接口文档。
- 新建管理员写 sys_admin，新建经纪人写 broker；注册直接创建待审核 broker，审核更新同一条记录。身份由原表决定，不能通过编辑角色互换；不得停用自己，必须保留一个启用管理员。
- 管理员姓名对应 admin_name，原表没有电话和门店；经纪人姓名对应 name，并同步已有 broker_name。账号接口管理员 id=-sys_admin.id，经纪人 id=broker.id，直接使用返回值；业务 brokerId 不变。version由原资料计算，修改时原样回传，不新增字段。旧凭证需重新登录，详见 TEAM_LOGIN.md。
- 状态变更、委托、预约和成交保留记录；归档和媒体移除是逻辑删除，媒体原文件保留以便恢复，需由部署方安排文件归档策略。
- PUT 更新业务记录时携带当前 version，过期返回 409。并发预约锁定经纪人；成交锁定房源，防止重复登记。
- 日期范围的结束日期包含当天；业务日期使用 Asia/Shanghai 口径。ID 在命名为 id/*Id 的字段中按字符串返回，页码、数量和金额保持数值。
- 文案服务在房源提交后调用，失败时房源照常保存；相同文案的成功结果复用，30秒内在途请求合并，失败/超时可重试。详情读取缓存，不重复调用 Python；请求日志保留失败及过期状态。
- 估价不会输入挂牌价格、客户预算或卖点文案；UNAVAILABLE 的价格/区间保持 null。估价不改变挂牌价或成交价。房源特征变化后历史预测 stale=true。
- 真实成交报表仅统计 CONFIRMED 且 verified=true 的记录；挂牌均价只是挂牌口径，AI 结果仅为历史参考。
- C 的复杂报表 SQL 可实现 `ReportExtension`；使用传入的 scopedSource 作为子查询，沿用门店/经纪人/日期过滤，基础数据与字段说明见 API.md。

## 本轮检查

本轮仅编译打包，跳过全部测试；未连接实际MySQL、导入数据库或启动服务联调。现有测试夹具仍基于旧账号表，尚未适配本次原表账号方案。后续业务验收见 INTEGRATION_CHECKLIST.md。
