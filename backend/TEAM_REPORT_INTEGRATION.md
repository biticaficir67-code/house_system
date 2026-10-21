# 队员报表合并说明

已合并队员的报表查询接口与 `database/house_system与后端统一+初始数据录入版.sql`。原 SQL 文件保留，不在合并时执行。

## 报表兼容

保留 `/api/report/custom` 下全部14个接口地址和原有主要响应字段，数据仍由 DataStore 转为 camelCase。图表查询新增可选 from/to（日期）和 storeId，与 `/api/reports` 共用权限范围约定。

- 必须登录且具备 report:read；管理员看全局，经纪人只看本店本人。管理员操作日志额外需要 system:manage。
- 所有图表通过同一组权限过滤后的数据来源查询；占比的分子、分母范围一致，不能改参数绕过范围。
- 实际带看仅 COMPLETED、BUSINESS；成交仅 CONFIRMED、verified=true、verificationStatus=APPROVED，排除演示带看、取消预约与未核实成交。
- 经纪人字段使用当前后端的 broker_name；账号状态仍由 sys_user 管理，不在 broker 中重复维护密码和审核信息。历史经纪人保留在业绩统计中，停用不会抹去过去业绩。
- 成交趋势与周期取 house_deal.deal_time，成交均价取 transaction_price_yuan；不以挂牌价或单独房源状态代替真实成交记录。
- 门店业绩先分别汇总房源、带看、成交，避免多表直连造成记录成倍放大。其他列表最多1000组，排行最多10条。
- 操作日志读当前后端 operation_log，返回 operatorId/operateType/targetId/content/operateTime 等兼容字段。旧日志未保存操作者当时的角色快照，operatorType 返回 null，不推断历史身份。

## 指标解释

- 房源指标按挂牌日期筛选，实际带看按 completedTime，成交按 dealTime；结束日期包含当天。
- conversion-funnel 展示当前在售数、发生过实际带看的房源数、核实成交房源数。这不是同一批房源的严格递减漏斗，前端不应把三个数直接换算为同批转化率。
- broker-performance 的 conversionRate 为核实成交笔数 / 实际带看次数，以百分比返回；分母为0返回 null，不表示客户成交概率，也不保证小于100%。
- house-age-distribution 保留队员的历史样本观察方式：以可见筛选样本最大挂牌日计算库龄，不是今天的实际库龄。展示时注明观察日口径。
- 客户复看按同一客户的实际完成带看次数分层；未知室数不归入“四室及以上”。

## 数据库文件边界

队员新上传的 SQL 是包含 DROP TABLE 的另一版全量底稿，不是可直接叠加的迁移脚本。它包含 broker.name、broker 上的独立登录字段、sys_admin/sys_oper_log，且 house_show 的时间字段也与当前后端不同。不要把它导入现有开发库后直接启动当前代码。

当前后端的启动依据继续是：空库导入 `database/house_system_full.sql`，然后依次执行 V001、V002、V003；已有业务库仅执行尚未执行的增量版本。若组内决定改以队员新全量底稿为唯一结构，应另行提供结构转换和数据迁移方案，不覆盖现有业务数据，不启用两套登录体系。

本次完成文件合并与编译打包检查；未运行测试、未执行数据库导入，也未确认真实 MySQL 上的所有图表查询。按 `INTEGRATION_CHECKLIST.md` 与 C 联调。
