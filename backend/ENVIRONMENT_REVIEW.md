# 全环境检查与待修问题

## 2026-10-26 更新

本轮已修正房源可选资料丢失、0厅/0卫、两处地图HTML提示、图片上传入口、注册申请进度与重提、审核明确分店。增加业务Excel/CSV导入和可解释客户匹配。现有H2底稿与初始化假设已对齐原账号表，但本轮未执行自动测试；Python临时目录问题保留。历史数据的未来日期、缺图片与挂牌日期由原管理页导入弹窗内的只读质量提醒显示，没有改写队员数据。具体操作和验证范围见 [FEATURE_UPDATE.md](FEATURE_UPDATE.md)。

以下是10月24日的检查原始记录，表中问题状态以本节更新为准。

检查时间：2026-10-24，约17:55，Asia/Shanghai。业务代码基于e879485；随后补充会话建表脚本与本文。检查目标是当前小组系统，未修改队员前端、Python、数据库SQL或报表原控制器。

## 当前可以使用

| 服务 | 本机地址 | 检查结果 |
| --- | --- | --- |
| 前端 | http://127.0.0.1:5174/ | 已打开，管理员可登录并看到看板、房源、权限页 |
| Java | http://127.0.0.1:8080/actuator/health | UP，使用local配置 |
| MySQL | 127.0.0.1:3306 | 独立8.4.13，导入队员latest，310房源、10经纪人、1管理员 |
| 文案 | http://127.0.0.1:5001/ai/health | 200，实际分析请求成功 |
| 价格 | http://127.0.0.1:8001/ready | ready=true，直接Python及Java转调均成功，返回DEMO_ONLY |

MySQL官方ZIP校验值与官网一致，mysqld.exe的Oracle签名有效。本机旧5.1.51程序和旧数据保留；旧MySQL服务仍指向不存在的D:\Java文件路径，本轮没有重装或改写它。

连接凭据只写入Git忽略的application-local.yml和新实例私有文件，未上传、未在报告中展示。独立库实际位于backend/target/mysql8/data；临时ASCII路径映射仅用于避开Windows MySQL启动器的中文路径问题。

**当前是独立联调环境：不要执行mvn clean或清理backend/target，它会影响新库和本机依赖。后续长期使用应先停库、备份并将数据迁到构建目录以外。** IDEA手动启动时须启用local配置；默认dev不读取该本机连接配置。

## 已完成的检查

- 前端生产构建通过；报表、12种图表及价格协议测试通过。
- 118项接口探测：管理员/经纪人身份、角色与人员、门店、工作台、五类业务列表及详情、待办、基础报表、14个自定义报表、日期校验、跨人员隔离、AI历史/预测、退出后凭证失效。补齐会话表后没有HTTP5xx；预期401/403/404/400按权限及参数要求返回。
- 15个独立账号场景通过：注册直接创建broker、待审核禁止业务登录、申请查询、拒绝、旧申请凭证失效、凭据恢复申请、重新提交、原记录审核分店、正式登录、本人资料更新、旧version冲突、停用与会话撤销。临时检查账号及其专属记录已清理，没有保留额外员工。
- Java转调Python估价、保存结果及读取历史通过；文案服务实际请求通过。
- 后端原有自动测试失败：H2测试库缺sys_admin，集成测试@BeforeAll就返回503；4个价格协议单元测试通过，业务回归套件未完整执行。
- Python现有8项测试中7项通过，交付测试的Windows临时目录清理报WinError32；单独复跑该交付测试再次出现同样错误。

上面是明确检查范围，不代表每个写入流程或浏览器交互都已覆盖。房源、委托、带看和成交的完整写入回归仍需补齐。

## 已处理的启动阻断

### P0：导入latest仍缺auth_session，所有正确账号登录503

实际重现：初次登录后端报Table 'house_system.auth_session' doesn't exist。latest.sql第932行把整个补充SQL拼成一行，行首是--，auth_session与application_session的DDL均被注释，常规导入只得到25张表。

已在backend提供src/main/resources/db/migration/direct_account_sessions.sql，并仅在新建独立库运行。它只CREATE TABLE IF NOT EXISTS auth_session，使用有符号账号编号，无sys_user外键。application_session已不被当前后端使用，无需补建。队员SQL未改；其他同学导入后也需执行这份补充脚本。

## 待修问题，按影响排序

| 优先级 | 问题与证据 | 实际影响 | 建议处理 |
| --- | --- | --- | --- |
| P1 | 编辑房源会丢未展示的字段：House.vue的bodyOfForm不提交businessArea/communityId/buildingType/layoutStructure/constructionStructure/ownershipType/internalAreaSqm/sourceListingId；HouseService.values会将缺失项作为null全量更新 | 普通编辑可能清空原始资料和价格模型特征，影响后续参考价；源码字段映射确认，未改动已有房源重现 | 前端完整回传原值，或明确定义PATCH与显式置空，补充编辑前后字段保留用例 |
| P1 | House.vue与HouseMap.vue将原始小区名直接传给Leaflet.bindTooltip；依赖的更新逻辑对字符串调用innerHTML，后端仅限制长度 | 不可信小区名可被当HTML解析，有存储型脚本注入风险；源码确认，未执行攻击载荷 | 使用HTMLElement并设置textContent，或统一可靠转义，补充恶意小区名用例 |
| P1 | 房源页没有图片上传入口，后端上架明确要求至少一张有效图片；当前库house_media为0行 | 新建房源无法只靠页面完成上架；现有在售房源编辑后自动下架重审，也可能无法重新上架 | 前端补媒体上传/封面入口，或小组明确修改业务规则；保持当前后端规则 |
| P2 | 经纪人注册页只提示提交成功，没有保存applicationToken，也没有申请状态、驳回原因与重提入口；后端15个相应场景实测通过 | 申请被拒后，使用现有页面无法自行完成修改重提 | 补申请查询和重提页面，独立保存申请凭证，不作为业务token |
| P2 | Permission.vue审核默认选择第一家门店，拒绝原因固定为“拒绝” | 管理员不能在审核时明确选择门店和说明问题；可能误分店 | 审核弹窗显式选门店、填写原因并携带version |
| P2 | House.vue用f.halls/f.bathrooms的真假判断转数字，0会变成null | 合法0厅/0卫资料在保存时丢失，且后续上架会提示缺字段 | 用空字符串/null判断，保留数字0；与房源字段保留问题一并修 |
| P2 | latest数据包含46条未来时间但已COMPLETED的带看、24条未来时间的已核实成交；另有2条房源listing_date为空 | 截至今天的看板为183次带看、21笔成交、308套纳入挂牌日期统计，与总量229/45/310不同，容易被误认为统计错误 | 请数据库同学校准演示日期和状态，勿通过删除后端日期过滤掩盖；应用写入已有PastOrPresent校验 |
| P2 | 后端schema-test.sql仍为旧账号结构，缺sys_admin和broker原账号字段；初始化测试还假定管理员有storeId | 后端自动回归无法检验当前版本，不能据打包成功认定无bug | 适配测试schema、账号编号、管理员无门店及原字段语义，恢复全套集成回归 |
| P2 | Python交付测试两次在TemporaryDirectory清理时WinError32，服务实际启动和估价成功 | Windows上自动检查不能全绿；需要继续定位仍占目录的句柄/子进程，不能认定模型不可用 | 针对Windows确认实际服务子进程退出及句柄释放后再清理，复跑验证；本轮不改队员Python |

## 需要同步的说明与优化项

- 根目录组员使用说明仍写sys_user、Broker123456和bk开头登录名，与本人要求的新原表方案不符；当前以backend/TEAM_LOGIN.md为准。新库admin及broker01～broker10的初始密码均为123456，改密后以原表当前密码为准。
- 前端主包约1.32MB、看板包约1.16MB，构建提示大包；这是加载性能优化项，不阻止功能运行。可后续拆分图表、地图和组件包。
- 本轮没有补前端页面或修改队员SQL、Python；上述待修项需要按模块分工落实。不能把合法的403权限拦截当成bug。

## 本机证据

- target/environment-api-results.json、environment-api-extra.json：118项脱敏接口结果，不含token。
- target/environment-account-workflow.json：15个账号场景状态码。
- target/environment-backend-tests.log、environment-python-tests.log、environment-python-delivery-recheck.log：失败证据。
- target/environment-backend.log：Java服务日志；账号凭据不在报告中。

上述target文件、本地配置和运行依赖不上传Git。提交的只有backend实现、会话补充脚本及对应说明。
