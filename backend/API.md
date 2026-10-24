# API 联调约定 v3（表格第二、三步）

新增接口及完整字段见 [WORKFLOW_IMPLEMENTATION.md](WORKFLOW_IMPLEMENTATION.md)，人工联调清单见 [INTEGRATION_CHECKLIST.md](INTEGRATION_CHECKLIST.md)。

所有业务路径以 `/api` 开头，业务 JSON 字段使用 camelCase；AI 的 inputSnapshot、priceInterval 等嵌套快照保留 Python 协议的 snake_case 键。请求头 `Authorization: Bearer <token>`。成功响应 HTTP 200：

```json
{"code":0,"message":"success","data":{},"requestId":"请求追踪编号"}
```

失败使用实际 HTTP 状态码，data=null：400 参数/业务状态不满足、401 登录失效、403 功能无权限、404 记录或接口不存在/无权查看、405 请求方法不支持、409 唯一约束/版本冲突、413 文件过大、429 登录限流、503 数据库未就绪。响应头同时提供 X-Request-Id。

列表 page 默认 1，size 默认 20，size 最大 100；返回 `{total,page,size,records:[]}`。total 是数值，命名为 id/*Id 的 64 位字段为字符串。PUT 是完整资料更新；传最新 version，版本冲突刷新后重试。金额统一元，面积平方米。精度有限的 JavaScript 代码可把输入金额作为十进制字符串提交。

## 1. 登录、密码、菜单

| 方法 | 路径 | 请求/响应 |
| --- | --- | --- |
| POST | /auth/bootstrap | 首次初始化，头 X-Bootstrap-Key；body: username/password/displayName/storeName |
| POST | /auth/login | username/password；返回 token/tokenType/expiresAt/user |
| GET | /auth/me | 当前身份、storeId、brokerId、roles、permissions、dataScope |
| POST | /auth/logout | 撤销当前 token |
| PUT | /auth/password | oldPassword/newPassword；成功后全部旧 token 失效 |
| GET | /auth/menus | 当前角色允许的菜单 key/name/permission |
| POST | /auth/register | username/password/confirmPassword/displayName/phone?；仅申请 BROKER，返回独立 applicationToken |
| POST | /auth/application-session | username/password；核验后获取20分钟申请凭证 |
| GET/PUT | /auth/application | X-Application-Token；本人申请及历史 / REJECTED 后修改并重申，version必传 |
| GET/PUT | /auth/profile | 本人资料 / displayName、phone、version，同步经纪人档案 |

新建账号长度 3–50，允许字母数字及 _.-；新密码至少 10 字符，UTF-8 最多 72 字节。首次初始化要求 sys_admin 为空、业务表和ADMIN角色齐备且 BOOTSTRAP_KEY 已配置。登录失败 5 次后，同一客户端地址与账号组合限流 5 分钟。

当前直接使用 sys_admin、broker 原表登录、启停和改密，不复制到 sys_user，也不通过 sys_user_role 绑定身份。初始账号与字段见 [TEAM_LOGIN.md](TEAM_LOGIN.md)，请求只传 username/password。新版本需重新登录；账号接口的管理员id为负的sys_admin.id，经纪人为正的broker.id，业务brokerId不变。version由原记录资料计算，必须原样回传，不自行递增；原表不增加version字段。管理员phone/storeId返回null，姓名最多50字，资料更新不可传非空phone。

注册即新增待审核broker；审核更新原记录，reject_reason最多255字，因此审核reason最多255字。会话使用latest已有且无sys_user外键的auth_session，不再读写application_session。

## 2. 用户、角色、门店

账号、门店写入和角色查询需要管理员的 system:manage。门店查询、经纪人查询和工作台允许两类人员使用，返回数据受本人范围限制。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET/POST | /stores | 查询范围内门店；新增 name/address/enabled |
| PUT | /stores/{id} | 更新 name/address/enabled |
| GET | /roles | 仅返回 ADMIN、BROKER 两个固定身份，含 id/code/name/permissions/dataScope/enabled；不提供新增/编辑角色接口 |
| GET | /workbench | 按当前身份返回今日业务统计、待核验与待核实记录、当日预约、近期到期委托 |
| GET | /users?keyword=&storeId=&accountStatus=&page=1&size=20 | 用户及角色，不返回密码哈希 |
| POST | /users | username/password/displayName/phone/storeId/brokerId?/roleIds |
| PUT | /users/{id} | displayName/phone/storeId/enabled/roleIds/version/reason；仅 ACTIVE/DISABLED，旧会话撤销 |
| GET | /users/{id} | 员工详情、角色、账号状态及version，无密码 |
| PUT | /users/{id}/review | approved/storeId?/brokerId?/reason/version；仅 PENDING，由管理员审核 |
| GET | /users/{id}/logs | 注册、审核及管理员操作历史，page/size |
| GET | /tasks?kind=&page=1&size=20 | 工作台完整待办，类型见实现说明 |
| PUT | /users/{id}/password | password；重置并撤销全部会话 |
| GET | /brokers?storeId=&unbound= | 默认有效经纪人；管理员可用 unbound=true 查待绑定旧档案 |

仅 ADMIN 管理员、BROKER 经纪人。每个账号只能选择一个身份，roleIds 为单元素数组。ADMIN 的 dataScope 固定 ALL；BROKER 固定 OWN，同时限制本店与本人 brokerId，列表、详情、工作台、报表及导出使用相同范围。经纪人显式提交其他 storeId/brokerId 作为业务归属时返回 403；筛选不可见范围返回空列表，猜详情 ID 返回 404。账号权限由服务端固定映射，数据库中遗留的自定义权限、数据范围不能扩大经纪人的权限。店长、财务、内勤的独立身份和可配置角色接口已移除。

ADMIN 拥有全部 permission：system:manage/system:read、house:read/house:write/house:verify/house:assign、owner:read/owner:write、customer:read/customer:write、showing:read/showing:write、deal:read/deal:write/deal:verify、ai:use、report:read。BROKER 拥有其中除 system:manage、house:verify、house:assign、deal:verify 外的权限，报表仅本人数据。接口鉴权独立于菜单显示。必须保留至少一个启用的 ADMIN，不得停用当前登录账号。业主和客户为业务资料，不是系统角色；没有业主或客户登录接口。仅经纪人可申请注册，PENDING/REJECTED/DISABLED 不得登录业务；ACTIVE 才允许。注册后的申请就是原broker记录，暂时没有门店，审核通过时在同一行分配门店；brokerId若传入必须等于该申请的broker.id。

创建BROKER未指定brokerId时直接新增broker；指定时只允许同店且没有完整登录凭据的原档案。创建ADMIN直接写sys_admin，brokerId必须为空；角色与原表一致，不支持通过角色编辑将管理员和经纪人互换。完整账号应使用原账号或重置密码。账号列表不返回密码；停用、调店、改密或重置密码使旧凭证失效。有未交接业务的经纪人不可直接跨店调动。

创建内部经纪人的示例（先由 GET /roles 获取 BROKER 的 id，下方门店 1、角色 2 均需换成实际 ID）：

```json
{"username":"broker001","password":"SetYourOwnPassword123","displayName":"经纪人甲","phone":"13800000000","storeId":"1","roleIds":["2"]}
```

### 工作台

`GET /api/workbench`，无需传用户 ID 或门店。响应 data：

| 字段 | 含义 |
| --- | --- |
| role / dataScope / date / canVerify | 当前身份、数据范围、上海时区日期、是否可审核 |
| counts.onSaleHouses | 可见范围内在售房源数 |
| counts.pendingHouseVerifications | 下架且待核验的房源数 |
| counts.activeCustomers | 活跃客户数 |
| counts.todayScheduledShowings | 今日尚未完成或取消的预约数 |
| counts.pendingDealVerifications | 有效且尚未核实的成交数 |
| counts.expiringEntrustments | 今天至未来 7 天内到期的有效委托数 |
| counts.unreadReminders | 有效/过期委托的未读提醒数 |
| pendingHouses | 最早录入的待核验房源，最多 5 条，含 id/communityName/storeId/brokerId/version |
| pendingDeals | 最早登记的待核实成交，最多 5 条，含 id/houseId/customerId/brokerId/transactionPriceYuan/version |
| todayShowings | 按预约时间排列的今日待执行预约，最多 5 条 |
| expiringEntrustments | 按到期日排列的近期到期委托，最多 5 条，含 id/ownerId/houseId/endDate |

管理员看到全局核验任务；经纪人看到本人提交后的处理进度，canVerify=false，不能调用核验接口。各列表只是工作台摘要，完整列表使用对应业务接口。

## 3. 房源与媒体

| 方法 | 路径 | 权限/用途 |
| --- | --- | --- |
| GET | /houses | house:read；page/size/district/communityName/status/storeId/brokerId/rooms/minPrice/maxPrice/minArea/maxArea/verificationStatus/sortBy/sortDirection |
| GET | /houses/{id} | 详情、媒体元数据、已保存文案分析 |
| POST | /houses | house:write；录入，默认 OFF + PENDING 核验 |
| PUT | /houses/{id} | house:write；更新资料，version 必传；重新核验，在售房源自动下架 |
| PUT | /houses/{id}/verification | house:verify；approved/reason/version |
| PUT | /houses/{id}/status | house:write；status/reason/version；只接受 ON_SALE/OFF |
| PUT | /houses/{id}/assignment | house:assign；storeId/brokerId/reason/version |
| DELETE | /houses/{id} | house:write；仅下架且无有效委托/未完成预约时归档 |
| GET | /houses/{id}/status-logs | house:read；创建、编辑、核验、交接、状态、成交时间线 |
| POST | /houses/{id}/media | house:write；multipart/form-data 的 file 字段 |
| GET | /houses/{id}/media/{id}/content | house:read；受权限保护的文件流 |
| DELETE | /houses/{id}/media/{id} | house:write；移除媒体，自动补选封面并重新核验 |
| PUT | /houses/{id}/media/{mediaId}/cover | house:write；version，设置图片封面 |
| PUT | /houses/{id}/media/order | house:write；mediaIds/version，全量有效媒体排序 |

房源资料示例：

```json
{
  "city":"杭州","district":"滨江","businessArea":"白马湖","communityName":"示例小区",
  "buildingAreaSqm":89.60,"rooms":3,"halls":2,"bathrooms":1,
  "floorLevel":"MIDDLE","totalFloors":18,"orientation":["S","N"],
  "decoration":"FINE","hasElevator":true,"propertyUsage":"RESIDENTIAL",
  "listingPriceYuan":3680000,"listingDate":"2026-10-20",
  "coreSelling":"近地铁","communityIntro":"人车分流","surrounding":"商场",
  "storeId":"1","brokerId":"1"
}
```

可选字段：communityId、longitude/latitude（成对）、buildingType、layoutStructure、constructionStructure、ownershipType、internalAreaSqm、sourceListingId。更新在原资料基础上带 version；门店和负责人用 assignment 接口交接。

floorLevel：LOW/MIDDLE/HIGH/UNKNOWN；decoration：ROUGH/SIMPLE/FINE/OTHER/UNKNOWN；orientation：E/S/W/N/NE/NW/SE/SW/UNKNOWN，UNKNOWN 仅能独占数组；propertyUsage：RESIDENTIAL/MIXED_USE/VILLA/GARAGE/UNKNOWN。未知室厅卫用 null；录入要求总楼层为正数。

核验状态为 PENDING/VERIFIED/REJECTED。仅 VERIFIED、有负责人、有正挂牌价的房源可上架。DEAL 状态只通过成交流程产生，撤销成交后回到 OFF。跨店交接需 ALL 数据范围，已有委托/带看/成交关联时先完成业务交接。

媒体最多 50 MB，支持 JPEG/PNG/WebP/MP4，校验实际文件头。前端带 Authorization 请求内容接口，通过 Blob URL 展示，不能把 token 放到 URL 参数中。逻辑移除保留历史文件。

## 4. 业主、委托、提醒

| 方法 | 路径 | 请求 |
| --- | --- | --- |
| GET/POST | /owners | 查询 keyword/page/size；新增 name/phone/address?/remark?/storeId/brokerId |
| GET/PUT/DELETE | /owners/{id} | PUT 同资料且带 version；删除前结束有效委托 |
| GET/POST | /owners/{id}/follow-ups | POST type/content/nextContactAt? |
| GET/POST | /owners/{id}/entrustments | POST houseId/startDate/endDate/exclusive/commissionRate?/remark? |
| PUT | /owners/{id}/entrustments/{entrustmentId}/end | reason |
| GET | /owners/reminders | 范围内到期提醒，readStatus=UNREAD/READ |
| PUT | /owners/reminders/{id}/read | 标记已读 |

读写分别需要 owner:read/owner:write。跟进 type=PHONE/VISIT/WECHAT/OTHER。nextContactAt 是 ISO 日期时间，填写时应为未来时间。

委托在同店业主与房源间建立；独家委托或同一业主重复委托在重叠日期范围内被拒绝。commissionRate 是 0–1 比例。7 天内到期的委托生成提醒，每天扫描并将过期的 ACTIVE 更新为 EXPIRED；重复扫描不重复提醒。已成交委托转为 COMPLETED；主动终止为 CANCELLED。

## 5. 客户与需求

| 方法 | 路径 | 请求 |
| --- | --- | --- |
| GET/POST | /customers | 查询 keyword/status/page/size；新增 name/phone/tags?/remark?/storeId/brokerId |
| GET/PUT/DELETE | /customers/{id} | PUT 带 version；有效成交或未完成预约阻止归档 |
| PUT | /customers/{id}/demand | districts/minPrice/maxPrice/minArea/maxArea/minRooms/maxRooms/decoration/expectedPurchaseDate/notes |
| GET | /customers/{id}/matches?limit=20 | 范围内在售房源，逐条附匹配条件说明 |
| GET/POST | /customers/{id}/follow-ups | type/content/nextContactAt? |

读取 customer:read，写入 customer:write。匹配还需要 house:read。tags 是字符串数组，最多 30 项。需求范围不能倒置；未填写需求项不参与筛选。客户状态 ACTIVE/DEAL/INACTIVE；有效成交关联客户转为 DEAL，撤销最后一笔成交后恢复 ACTIVE。

## 6. 预约与实际带看

| 方法 | 路径 | 请求 |
| --- | --- | --- |
| GET | /showings | houseId/customerId/status/from/to/page/size |
| GET | /showings/{id} | 含历次回访 |
| POST | /showings | houseId/customerId/brokerId/appointmentTime/remark? |
| PUT | /showings/{id} | 改期 appointmentTime/version/remark? |
| PUT | /showings/{id}/complete | actualTime/version/remark? |
| PUT | /showings/{id}/cancel | reason/version |
| POST | /showings/{id}/follow-ups | content/intention/nextContactAt?/version |

权限 showing:read/showing:write。预约必须在未来、房源必须在售、客户与房源同店、执行经纪人必须属于同店。经纪人 30 分钟预约时段防重。actualTime 不晚于当前时间。状态 SCHEDULED/COMPLETED/CANCELLED；intention=HIGH/MEDIUM/LOW/UNKNOWN。完成后才可回访，房源成交时自动取消未完成预约。

## 7. 独立成交记录

| 方法 | 路径 | 请求 |
| --- | --- | --- |
| GET | /deals | houseId/verified/verificationStatus/page/size |
| GET | /deals/{id}/logs | 核实、更正和撤销历史，page/size |
| GET | /deals/{id} | 独立成交信息 |
| POST | /deals | houseId/customerId/brokerId/dealTime/transactionPriceYuan/commissionYuan?/remark? |
| PUT | /deals/{id} | dealTime/transactionPriceYuan/commissionYuan?/remark?/version；核实前可编辑 |
| PUT | /deals/{id}/verification | approved/reason/version，需要 deal:verify |
| PUT | /deals/{id}/cancel | reason/version |

读取 deal:read，登记/编辑/撤销 deal:write。dealTime 不晚于当前时间，成交金额为正，佣金不得高于成交金额。一笔有效成交将房源转为 DEAL，挂牌价保持独立原值。新增默认 verified=false、verificationStatus=PENDING；管理员通过为 APPROVED，拒绝为 REJECTED，拒绝后编辑更正回到 PENDING。只有待核实可审核，经管理员核实后才纳入真实成交统计；核实后的成交撤销也需要管理员的 deal:verify。经纪人不能核实自己的成交。撤销保留原成交记录，房源恢复下架，委托和取消的预约不会自动复活。

## 8. AI 接入

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| GET | /ai/houses/{houseId}/text-analysis | 读已保存文案结果，house:read |
| POST | /ai/houses/{houseId}/text-analysis | 手动重试，ai:use |
| POST | /ai/houses/{houseId}/price-predictions | 用户主动触发历史参考估价，ai:use |
| GET | /ai/houses/{houseId}/price-predictions | 最近 100 条价格历史，house:read |
| GET | /ai/houses/{houseId}/requests | 最近 100 次调用，包括失败、过期，ai:use |

新增/编辑文本在房源提交后异步分析；页面可轮询 GET 文案结果。状态 PENDING/SUCCESS/FAILED，stale=true 表示对应文本版本已变更。仅存 summary/advantages/disadvantages/wordFreq 和过程状态，页面不展示 Python 模型内部 topic/method。

估价输入取自已保存房源，映射为 Python 严格 snake_case；挂牌价、客户预算、文本均不发送。缺少必要户型字段时 400 提示补充。Python 正常结果 DEMO_ONLY/UNAVAILABLE；前者是历史未核验挂牌样本参考，后者的价格和区间保持 null。HTTP/网络/协议失败返回业务状态 FAILED，价格为 null，并保存请求失败日志。快照、featureHash、modelVersion、dataVersion、priceBasis、提示均保存，修改模型输入后旧结果 stale=true。

Java 调用的真实 Python 路径为 5001 的 /ai/analyze 和 8001 的 /ai/predict/price。连接超时 2 秒，读取超时分别 5/10 秒。不会训练或替换 D 的模型。

## 9. 报表与 C 的接入点

全部需要 report:read，支持 from/to（YYYY-MM-DD）、storeId、groupBy。两种身份均可使用；管理员为全局/筛选门店范围，经纪人为本店本人范围，包括 CSV 导出，指定他人门店不扩大结果。结束日期包含当天，后端始终叠加账号数据范围。

| GET 路径 | 指标/统计口径 |
| --- | --- |
| /reports/overview | 房源库存数、实际带看数、核实成交数、AI 请求数 |
| /reports/inventory | 房源库存分组计数，含原始导入房源 |
| /reports/prices | 挂牌总价/单价均值、有效挂牌价格样本数 |
| /reports/showings | 新业务里已完成实际带看数，排除 LEGACY_DEMO |
| /reports/deals | 有效且核实成交数、总金额、均价 |
| /reports/ai | AI 请求计数，按状态等分组 |
| /reports/houses/export | UTF-8 CSV，最多 10000 条范围内房源，无客户/业主隐私字段 |

groupBy 支持 DISTRICT/COMMUNITY/STORE/BROKER/STATUS/MONTH；默认库存/价格按区域，实际带看/成交按月，AI 按状态。房源报表日期取 listingDate、实际带看取 showTime、成交取 dealTime、AI 取 createdAt。没有分组样本时返回空 records，平均值无有效样本时为 null。

C 可实现 `system.report.ReportExtension` Spring Bean。框架传入已按门店/负责人/日期筛选的 scopedSource SQL 与绑定参数，C 在该子查询之上实现转化率、小区分布等复杂统计；不支持的 kind 返回 Optional.empty()，继续使用基础报表。不要把原始演示带看当真实转化样本，也不要把挂牌均价写成真实成交走势。

## 推荐联调顺序

1. 初始化管理员，登录获取 token，创建门店/账号，或为账号绑定原有 brokerId。
2. 经纪人录入房源（OFF/PENDING）→ 管理员核验 → 经纪人上架。修改资料后自动重新核验，在售时先下架；核验拒绝保留原因和时间线。
3. 录入业主并建立委托，录入客户需求与跟进。
4. 预约 → 实际完成带看 → 回访。
5. 经纪人登记成交 → 管理员核实 → 双方查看各自范围的真实成交报表；未核实时经纪人可更正或撤销，已核实后的纠错撤销交由管理员。
6. 启动 D 的两个 Python 服务，验证文案分析、历史估价与失败重试。

当前组内基线及文案请求合并规则见 [TEAM_ALIGNMENT.md](TEAM_ALIGNMENT.md)。相同文案成功结果复用；30秒内处理中请求不会重复调用 Python，失败或超时可重试，接口路径与返回字段不变。
