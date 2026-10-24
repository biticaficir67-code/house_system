# 表格第二、三步的后端实现说明

> 2026-10-24 实际联调补充：实际导入已确认：队员latest.sql第932行把auth_session等补充DDL拼成一行注释，常规导入不会创建会话表。导入后手动执行 `src/main/resources/db/migration/direct_account_sessions.sql` 补齐auth_session；该脚本只做CREATE TABLE IF NOT EXISTS，不读取sys_user、不删除数据。历史V001～V003仍不可执行。本轮已仅在新建独立库执行此补充脚本。

依据：`housesystem.xlsx` 的 Sheet1，第二步 C2:C10、第三步 D2:D10。角色固定为 ADMIN 与 BROKER，所有后端源代码继续使用 `src/main/java/system`，业务目录内直接维护 Controller、Service、Models。

本文是后端需求实现对照，部分接口还没有前端入口。2026年10月23日之后的当前演示基线与本轮修正见 [TEAM_ALIGNMENT.md](TEAM_ALIGNMENT.md)，启动与账号以 [TEAM_LOGIN.md](TEAM_LOGIN.md) 为准。

## 1. 本次实现范围

| 表格部分 | 已落实的后端能力 | 对应文件 |
| --- | --- | --- |
| 登录与权限 | 四种账号状态、经纪人申请、独立申请凭证、拒绝后重新申请、审核历史、管理员初始化、正常账号才能登录 | auth/AuthService、RegistrationService、AuthController、AuthModels；config/SecurityConfiguration |
| 用户与经纪人 | 审核时为原broker记录分配门店、员工筛选/详情、本人资料、资料同步、启停与恢复、重置密码、旧会话撤销、员工操作日志 | system/SystemService、SystemController、SystemModels；common/AuditService |
| 房源 | 核验状态筛选、创建时间/价格/面积排序、封面、媒体排序和数量限制、媒体变更后下架重审、上架缺失项、委托摘要、最近核验、价格和交接记录 | house/HouseService、HouseModels、MediaService、MediaController |
| 业主 | 可见范围内重复电话提示、委托冲突、共同委托、跟进历史、最新联系计划、今日/逾期待办、管理员交接、归档限制 | owner/OwnerService、OwnerController、OwnerModels；common/FollowUpService |
| 客源 | 重复电话提示、需求校验、逐项匹配解释、暂不跟进及原因、管理员交接、跟进计划与待办 | customer/CustomerService、CustomerController、CustomerModels |
| 带看 | 预约、改期、完成、取消、30分钟冲突检查、首次待回访、再次联系待办、待执行预约交接、历史执行人保护 | showing/ShowingService、ShowingController；common/TaskService |
| 成交 | 有效成交防重、核实待处理/通过/拒绝、更正后重新核实、不可直接编辑已核实成交、撤销联动和完整审核历史 | deal/DealService、DealController；common/AuditService |
| AI | 原有事务提交后分析、缓存读取、主动估价、超时/失败/过期保护继续使用；调用日志补存房源版本 | ai/AiService；原有 AiFeatures、AiHouseListener |
| 报表/工作台 | 原有日期/门店/本人范围/导出继续使用；新增注册、联系、回访待办摘要和分页完整列表 | report/ReportService；system/SystemService；common/TaskService |

页面按钮、弹窗、跳转和输入保留由前端实现。本次提供对应的权限、状态、原因、版本号、待办列表和错误返回，不新增前端代码。C 的复杂 SQL 和 D 的模型继续由对应组员维护。

## 2. 当前数据库与原字段

当前基线为house_system_latest.sql，使用MySQL8.0+。不额外执行历史V001～V003，不再提供V004账号复制。原脚本仅保留为历史参考；当前账号改为sys_admin、broker原表，字段与编号见[TEAM_LOGIN.md](TEAM_LOGIN.md)。本次没有连接或修改数据库。

accountStatus是接口派生值：管理员由is_deleted决定；经纪人由audit_status与account_status共同决定。enabled为ACTIVE的兼容展示值，不是新增数据库列。申请即broker原记录，审核更新同一条记录。账号version由原资料计算，修改时回传当前值，不假设从0开始。

## 3. 注册与员工使用方式

### 3.1 初始化与注册

当前 latest 已有管理员，直接登录即可。只有业务表与ADMIN角色齐备、sys_admin为空时才用bootstrap初始化ADMIN，再开放申请。初始化、注册、审核及账号调整共用 ADMIN 角色行锁，不依赖 sys_bootstrap，避免并发初始化或人员操作冲突。

`POST /api/auth/register`：

```json
{"username":"broker001","password":"YourOwnPassword123","confirmPassword":"YourOwnPassword123","displayName":"经纪人甲","phone":"13800000000"}
```

只接受经纪人申请，不接受 role、storeId 等额外字段。账号唯一；已存在账号请核验原账号后查看或重新提交，不能重复注册。返回 `applicationToken`、`expiresAt`、`application`；没有普通业务 token。

### 3.2 查看申请和重新申请

- `POST /api/auth/application-session`：username/password；成功后获取 20 分钟的独立申请凭证，仅 PENDING、REJECTED 可使用。
- `GET /api/auth/application`：头 `X-Application-Token`；返回本人申请和最近 100 条历史。
- `PUT /api/auth/application`：同一申请凭证；displayName、phone、version，另可提交 password/confirmPassword 修改密码。只有 REJECTED 可重新提交，回到 PENDING，账号不变。成功后旧申请凭证撤销，使用返回的新凭证。

申请凭证不被 Bearer Token 过滤器认可，不能访问房源等业务。该阶段不要携带失效的 Authorization 头。拒绝原因仅在正确账号密码或有效申请凭证核验后返回。密码哈希和任何凭证明文均不写操作日志。

### 3.3 管理员审核

`GET /api/users?keyword=&storeId=&accountStatus=PENDING&page=1&size=20` 查询；`GET /api/users/{id}` 取详情与 version。

`PUT /api/users/{id}/review`：

```json
{"approved":true,"storeId":"1","reason":"已核对内部员工身份","version":1234567890123}
```

示例version必须替换为详情接口返回的当前值。通过时storeId必填，在原broker记录内分配；brokerId若传入必须等于该申请的broker.id，不得另绑人员。拒绝用approved=false，reason必填且最多255字。审核需要当前version且仍为PENDING，不重复新建档案；已存在门店的申请在原店审核。

`GET /api/brokers?storeId=` 默认只列出原broker中审核通过且启用、有完整登录凭据的人员，供新业务选择。管理员使用 `unbound=true` 单独查询原始待绑定档案；这些档案需先绑定并启用账号才能接收新业务。审核通过后申请凭证失效，申请人转到普通登录入口。

### 3.4 日常账号管理

- ACTIVE、DISABLED 才能由普通员工管理接口更新；PENDING/REJECTED 必须走审核，不能用 enabled=true 绕过审核。
- `PUT /api/users/{id}` 在原有字段基础上必传 version、reason。enabled=false 为停用，true 为恢复。旧业务和操作人不删除，变更立即撤销登录与申请会话。
- `GET/PUT /api/auth/profile` 查看/修改本人姓名、电话（管理员原表无电话，不能传非空值）；PUT 必传 version，不接受角色、门店、账号状态。姓名电话同步经纪人档案。
- `GET /api/users/{id}/logs?page=1&size=20` 为管理员查询注册、审核、资料、停用/恢复、重置密码记录；不支持跨账号表的身份转换。
- 始终保留一个可用管理员，不可停用自己。跨店前检查未完结业务：非成交房源、未归档业主、跟进中客户、待执行预约、未核实有效成交需先交接。已完成带看、已核实成交保留原店与执行人，不改写历史；调整门店后经纪人仅能访问新店本人数据，管理员仍可查询旧记录。

## 4. 房源、媒体和交接

- `GET /api/houses` 新增 verificationStatus=PENDING/VERIFIED/REJECTED、sortBy=CREATED_AT/PRICE/AREA、sortDirection=ASC/DESC。排序使用固定白名单。
- 列表新增 coverMediaId，图片内容仍通过受保护的 `/api/houses/{houseId}/media/{id}/content` 读取。详情媒体有 isCover、sortOrder；另有 latestVerification、entrustmentSummary、operations。
- 单个文件最多 50MB，每房源最多20张图片、3个视频。首张有效图片自动作封面，删除封面时选择下一张。排序包括全部有效媒体 ID，不得重复或混入其他房源。
- `PUT /api/houses/{houseId}/media/{id}/cover` body `{ "version": 当前房源版本 }`。
- `PUT /api/houses/{houseId}/media/order` body `{ "mediaIds": ["1","2"], "version": 当前房源版本 }`。
- 新增/移除媒体、换封面或排序均令房源 OFF/PENDING，并递增房源 version；已成交房源不允许调整媒体。上传返回 houseVersion，其他操作后重新取详情。批量上传完毕后再统一核验、上架，不要每上传一张就审核。
- 上架检查核验通过、有效门店与负责人、区域/小区/面积/室厅卫、正挂牌价和至少一张图片。缺少时返回具体项目。
- 普通下架若还有待执行预约则阻止并提示先处理；编辑资料或媒体导致的自动下架保留原预约，待重新核验上架或由人员取消/改期，不静默删除。
- 房源交接继续使用原 assignment 接口，记录原/新门店与经纪人；不会自动改写客户、业主、历史带看和成交。

## 5. 业主、客户、委托和跟进

### 5.1 重复提醒与负责人交接

- `GET /api/owners/duplicates?phone=&excludeId=`、`GET /api/customers/duplicates?phone=&excludeId=` 返回可见范围内最多20条相同电话档案；不自动合并，不通过提示泄露其他人的资料。前端保存前按需查询。
- `PUT /api/owners/{id}/assignment`、`PUT /api/customers/{id}/assignment`：brokerId/version/reason，仅管理员。普通资料 PUT 不再允许悄悄更改负责人。业主交接会同步有效委托，历史跟进与已结束委托保留原记录。
- 对应 `GET /{owners|customers}/{id}/logs?page=&size=` 查询交接等操作历史。

### 5.2 共同委托

建立第一位业主委托后，其他共有业主新增委托时传 `jointWithId` 指向同一房源、自己有权访问的有效委托。日期、独家约定、佣金比例必须完全一致，共用 agreementId；相同业主不能重复加入重叠委托，另起的冲突独家委托仍被拒绝。不传 jointWithId 就是独立委托。

结束委托接口针对当前业主参与记录；退出共同委托不自动取消其他共有业主的参与记录。成交会结束该房源所有有效委托。

### 5.3 客户需求、状态与待办

需求匹配按原有权限和在售状态筛选，新增 matchReasons 数组与具体的 matchExplanation：列出已填区域、价格、面积、室数、装修及实际值。未填写项不限，无结果为空列表。

`PUT /api/customers/{id}/status`：status=ACTIVE/INACTIVE、reason、version。暂不跟进记录原因，需先处理待执行预约。DEAL 状态由成交模块维护，不能普通改状态伪造成交。

业主/客户每次新增跟进保留历史，并覆盖档案上的最新 nextContactAt，递增父记录 version；不填 nextContactAt 会清除旧计划。因此旧待办不反复出现，旧跟进也不丢失。客户 INACTIVE、DEAL 或已归档后不进入日常联系待办。

## 6. 预约、带看与成交

- 原预约防重继续使用同一经纪人30分钟窗口与事务锁。仅跟进中客户可新增预约，仅在售房源可改期。
- `PUT /api/showings/{id}/assignment`：brokerId/version/reason，仅管理员；只交接 SCHEDULED，检查接收人的时段冲突。已完成带看不可改执行人。交接后需同时安排相应房源/客户的权限归属，避免新经纪人只有预约而没有相关资料权限。
- `GET /api/showings/{id}/logs` 查询交接历史。完成后的多次回访保留历史，最新 nextContactAt 进入再次联系待办。
- 成交 verificationStatus 为 PENDING/APPROVED/REJECTED，与有效性 status=CONFIRMED/CANCELLED 分开。列表支持 verificationStatus 筛选。
- 登记后 PENDING；管理员只核实 PENDING。拒绝后经纪人编辑更正，重新回到 PENDING；已核实不可直接编辑。更正成交日期同时同步房源成交状态时间。
- 成交核实/更正/撤销历史用 `GET /api/deals/{id}/logs` 查看。撤销保留原核实状态供追溯，但有效性变 CANCELLED、verified=false，真实统计排除。
- 所有关联更新继续在同一事务。历史挂牌价、成交金额、AI参考价分开保存。

## 7. 工作台及完整待办接口

`GET /api/workbench` 保留原统计，增加 tasks，每类包含 total/page/size/records，摘要最多5条。

`GET /api/tasks?kind=类型&page=1&size=20` 获取完整分页列表：

| kind | 含义 |
| --- | --- |
| REGISTRATION | 待审核经纪人，仅管理员 |
| OWNER_TODAY / OWNER_OVERDUE | 今天应联系 / 今天之前已逾期的业主 |
| CUSTOMER_TODAY / CUSTOMER_OVERDUE | 今天应联系 / 今天之前已逾期的活跃客户 |
| SHOWING_TODAY | 今日待执行预约 |
| SHOWING_OVERDUE | 预约时间已过仍未处理 |
| SHOWING_UNREVIEWED | 已完成的新业务带看，尚无任何回访 |
| SHOWING_RECONTACT | 已完成带看中，最新下次联系时间早于明日的记录，含逾期 |
| HOUSE_VERIFICATION | 下架待核验房源 |
| DEAL_VERIFICATION | 有效待核实成交 |

本人数据范围与列表、详情、报表、导出一致。OWNER/CUSTOMER 今日与逾期互斥；SHOWING_TODAY 与 SHOWING_OVERDUE 可能重叠，请勿相加当作去重总数。日期按 Asia/Shanghai。

## 8. 实际运行与交付边界

本次仅进行源码编译打包，不运行自动化测试，也不自动执行真实数据库迁移。已有测试夹具随字段和上架图片规则同步维护；这不代表新流程已通过自动化或人工验收。

真实 MySQL 8 增量 DDL、前端页面、C 的复杂 SQL、D 的真实 Python 服务需要按 `INTEGRATION_CHECKLIST.md` 联调。现有 Java HTTP 适配器保留真实服务地址配置，不用模拟结果冒充实际服务成功。队员新增报表接口已另行合并，兼容和数据口径见 `TEAM_REPORT_INTEGRATION.md`。
