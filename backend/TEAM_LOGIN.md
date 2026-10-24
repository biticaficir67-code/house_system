# 组员登录与账号字段说明

更新：2026-10-24。按当前要求，直接使用队员数据库里的 `sys_admin`、`broker`，不再复制账号到 `sys_user`，不读取 `sys_user_role`。数据库基线为 `database/house_system_latest.sql`，MySQL 8.0+。

## 登录

| 身份 | 原表 | 最新 SQL 中的初始账号 | 初始密码 |
| --- | --- | --- | --- |
| 管理员 | sys_admin | admin | 123456 |
| 经纪人 | broker | broker01～broker10 | 123456 |

以上仅指仓库 SQL 的初始值；实际数据库已改密时，以原表当前密码为准。以前 sys_user 中的 Broker123456、bk 开头账号不再作为登录依据。请求无需传角色，同名同时出现在两个原表时返回409，须核对并消除重名。

```http
POST /api/auth/login
Content-Type: application/json

{"username":"admin","password":"123456"}
```

响应继续使用 token/tokenType/expiresAt/user。业务请求使用 `Authorization: Bearer <token>`。切换到本版后，旧凭证失效，需要重新登录。

兼容原表已有明文密码；成功核验凭据后在原 password 字段内升级为 BCrypt 摘要。新建、重置、修改密码使用 BCrypt，新密码至少10字符、UTF-8不超过72字节；不新增密码列、不批量重置现有密码。

## 原字段如何使用

| 用途 | 管理员 sys_admin | 经纪人 broker |
| --- | --- | --- |
| 主键 | id | id，同时是业务 broker_id |
| 登录 | username、password | username、password |
| 姓名 | admin_name，最多50字 | name，最多64字；latest 同时有 broker_name，修改时同步 |
| 电话、门店 | 原表无这些字段，返回null | phone、store_id、dept；dept同步门店名称 |
| 审核 | 管理员不走入职审核 | audit_status：0待审核、1通过、2拒绝；reject_reason最多255字 |
| 启停 | is_deleted：0可用、非0停用 | account_status：1可用、0停用；还须审核通过才能登录业务 |
| 时间 | create_time、update_time | created_at、updated_at |

管理员资料请求不要传非空 phone；不能通过编辑角色把某条原记录搬到另一张表。固定 ADMIN/BROKER 的权限由后端决定，sys_role仅提供角色元数据及账号事务行锁，不再通过账号关联表分配身份。

注册直接新增一条待审核 broker，先无门店；审核通过后更新同一条记录并分配门店，不另建账号或经纪人。拒绝后可凭申请凭证修改并重提。管理员直接开通经纪人也写入 broker；已有完整凭据的档案应登录或重置密码，不重复开通。

## 接口编号、版本与会话（后端联调）

两张原表的id可能重复。保持 `/api/users/{id}` 及前端既有字段格式，账号接口用 `-sys_admin.id` 表示管理员、`broker.id` 表示经纪人，例如管理员id=-1、经纪人id=1。调用方直接使用接口返回的id，不自行转换。业务记录的brokerId始终为原broker正数主键。

version是由原记录资料计算的并发校验值，不新增数据库version列；修改时原样回传当前version，不自行加1。密码不进入该资料摘要，也不进入日志和账号响应。

继续使用latest已有的auth_session，user_id保存上述账号编号，不连接sys_user；latest该列没有外键。业务凭证b2、申请凭证p2用途隔离，申请凭证20分钟，仅用于本人申请；application_session不再读写。凭证摘要关联原表当前密码、状态和门店，改密、停用、调店后旧凭证不能继续使用。登录/申请凭证不写日志。

新账号操作历史使用admin_account/broker_account资源类型；operation_log.actor_id使用上述账号编号。管理员操作同时写原sys_oper_log：operator_id为真实sys_admin.id、operator_type=1。历史日志不按新编号规则重新解释，不批量改写历史业务操作人。

## 启动与数据库边界

已有latest数据库直接配置DB_URL、DB_USERNAME、DB_PASSWORD后启动后端；不额外执行历史V001～V003。后端不自动建表或导库，旧版带sys_user外键的auth_session结构不能直接用于本版。旧库需要先核对实际结构；不能用全量SQL覆盖需要保留的数据。

已有sys_admin记录无需bootstrap。只有已具备业务表和ADMIN角色、但sys_admin为空时，才临时配置BOOTSTRAP_KEY并调用初始化，完成后清空。旧账号迁移开关和V004已移除。

本次没有连接实际MySQL、导入SQL、删除数据库表或修改初始数据文件。数据库中已有sys_user等历史表可暂留，运行时不会访问；实际清理需另行检查外键、旧数据和其他服务依赖。

## 常见问题

- 401：核对原表username/password，或重新登录获取新凭证。
- 403：检查原表启停、broker审核状态、角色启用及门店是否可用。
- 409：检查两个账号表是否重名，或刷新资料后回传最新version。
- 503或表/列缺失：核对连接的库与latest结构，勿重新运行历史账号迁移。
