# 后端与小组当前进度对齐说明

> 2026-10-24 实际联调补充：实际导入已确认：队员latest.sql第932行把auth_session等补充DDL拼成一行注释，常规导入不会创建会话表。导入后手动执行 `src/main/resources/db/migration/direct_account_sessions.sql` 补齐auth_session；该脚本只做CREATE TABLE IF NOT EXISTS，不读取sys_user、不删除数据。历史V001～V003仍不可执行。本轮已仅在新建独立库执行此补充脚本。

整理日期：2026-10-24。依据web小组10月22日晚及之后的聊天、10月23日使用说明、main的f34ea03版本，并按本人最新要求改用队员原账号表。

## 当前基线

- 周四晚发布报表中心和价格预测更新；周五确认现有代码、演示及house_system_latest.sql已上传。
- 继续在现有系统上完善后端；新增业务功能先沟通。本次账号表调整依据本人明确要求。
- 仅修改backend内的代码与说明；不修改前端、Python、database文件及报表同学ReportCustomController.java。

## 后端调整

| 部分 | 当前实现 |
| --- | --- |
| 账号来源 | 管理员直接sys_admin，经纪人直接broker；移除sys_user、sys_user_role运行时依赖及旧账号复制服务 |
| 登录、审核、启停、密码 | 使用原表username/password、is_deleted、audit_status、account_status、reject_reason；保留接口路径及token/user响应 |
| 人员与申请 | 注册即新增待审核broker；审批更新原记录并分配门店；列表、待办、负责人资格统一读取原字段 |
| 并发与身份 | ADMIN角色行锁串行账号管理；负数管理员账号编号避免两表主键冲突；资料version由原字段计算，无新增列 |
| 会话 | auth_session保存两类账号会话；申请凭证用途独立；旧凭证失效，需重新登录；不再使用application_session |
| 日志 | 账号历史使用新资源类型；管理员操作同步队员sys_oper_log原字段，不推断历史身份 |
| 姓名与报表 | 查询broker.name，姓名写入同步latest已有broker_name；管理员操作日志读取sys_oper_log |
| 文案分析 | 同房源、同文案成功结果复用；30秒内在途请求合并；失败或超时可重试 |
| 业务与价格接口 | 保留既有业务流程、权限范围、统计口径及价格协议；运行报表由ReportController提供 |

字段映射、初始登录信息、编号约定及数据库边界见[TEAM_LOGIN.md](TEAM_LOGIN.md)。本次不新增数据库字段，不执行导库、删除表、批量改密或历史数据迁移。

## 现有页面边界

委托、需求、匹配、跟进、待办、成交和媒体接口继续保留，页面尚未覆盖所有入口。新房源上架仍要求图片，当前房源页没有上传入口时，可通过已有Swagger媒体接口上传后核验上架；增加页面或改变规则由小组决定。

当前前端5174、Java8080、文案5001、价格8001、独立MySQL3306均已启动。后续按本人要求运行了真实MySQL联调及现有测试，源码已推送；检查结果与测试失败原因见 [ENVIRONMENT_REVIEW.md](ENVIRONMENT_REVIEW.md)。
