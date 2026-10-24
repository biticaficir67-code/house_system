# 队员报表合并说明

已合并队员的报表查询接口与 `database/house_system与后端统一+初始数据录入版.sql`。原 SQL 文件保留，不在合并时执行。

## 报表兼容

`ReportCustomController.java` 已恢复为报表同学最初提交的原稿，后续不在此文件中做后端兼容修改。之前增加的权限、筛选、字段适配及统计口径调整已完整移至 `ReportController.java`。

运行时由 `ReportController` 同时提供 `/api/reports` 和 `/api/report/custom`；启动类仅将原稿控制器从组件扫描中排除，避免同一路径重复注册。原稿保留供同学维护、对照，实际联调使用上面的统一入口，不能同时启用两个控制器。

保留 `/api/report/custom` 下全部14个接口地址和原有主要响应字段，数据仍由 DataStore 转为 camelCase。图表查询新增可选 from/to（日期）和 storeId，与 `/api/reports` 共用权限范围约定。

- 必须登录且具备 report:read；管理员看全局，经纪人只看本店本人。管理员操作日志额外需要 system:manage。
- 所有图表通过同一组权限过滤后的数据来源查询；占比的分子、分母范围一致，不能改参数绕过范围。
- 实际带看仅 COMPLETED、BUSINESS；成交仅 CONFIRMED、verified=true、verificationStatus=APPROVED，排除演示带看、取消预约与未核实成交。
- 经纪人姓名读取队员 broker.name（响应仍为 brokerName），账号、密码与审核直接使用 broker 原字段；管理员直接使用 sys_admin。历史经纪人保留在业绩统计中，停用不会抹去过去业绩。
- 成交趋势与周期取 house_deal.deal_time，成交均价取 transaction_price_yuan；不以挂牌价或单独房源状态代替真实成交记录。
- 门店业绩先分别汇总房源、带看、成交，避免多表直连造成记录成倍放大。其他列表最多1000组，排行最多10条。
- 管理员操作日志直接读取 sys_oper_log 的 operator_id/operator_type/operate_type/target_id/content/operate_time；新管理员操作同步写入原字段。operation_log仍保存业务事件及前后快照，不按当前身份推断历史操作者类型。

## 指标解释

- 房源指标按挂牌日期筛选，实际带看按 completedTime，成交按 dealTime；结束日期包含当天。
- conversion-funnel 展示当前在售数、发生过实际带看的房源数、核实成交房源数。这不是同一批房源的严格递减漏斗，前端不应把三个数直接换算为同批转化率。
- broker-performance 的 conversionRate 为核实成交笔数 / 实际带看次数，以百分比返回；分母为0返回 null，不表示客户成交概率，也不保证小于100%。
- house-age-distribution 保留队员的历史样本观察方式：以可见筛选样本最大挂牌日计算库龄，不是今天的实际库龄。展示时注明观察日口径。
- 客户复看按同一客户的实际完成带看次数分层；未知室数不归入“四室及以上”。

## 数据库文件边界

当前演示基线为database/house_system_latest.sql。账号直接读取sys_admin、broker；已导入latest的库无需历史账号迁移。V001～V003只保留为历史参考，V004与旧账号复制服务已移除。队员SQL、前端及Python保持原样，详见[TEAM_ALIGNMENT.md](TEAM_ALIGNMENT.md)和[TEAM_LOGIN.md](TEAM_LOGIN.md)。

本次完成文件合并与编译打包检查；未运行测试、未执行数据库导入，也未确认真实 MySQL 上的所有图表查询。按 `INTEGRATION_CHECKLIST.md` 与 C 联调。
