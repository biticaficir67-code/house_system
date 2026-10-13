# 价格预估 API 对接说明 v1.0

## 分工与调用流程

Vue3 页面提交房源信息给 Spring Boot；Java 完成登录权限校验、字段映射后调用独立 Python 服务 `POST /ai/predict/price`，再把结果交给 Vue3。D 交付 Python 服务、模型和协议；A 实现 Java HTTP 调用与数据库保存，B 展示结果与提示。当前未实施 Java/Vue 代码。

Python 默认本地端口 8001，文本卖点服务继续使用自身配置。启动方式见 `ai_service/price_predict/README.md`。同机 Java 调用 `http://127.0.0.1:8001/ai/predict/price`；不同机器由部署同学配置 Python 内部服务地址，不应写死到前端。

## 请求

Content-Type：`application/json`。版本固定 `schema_version="1.0"`，调用方提供 `request_id`。完整字段和枚举在同目录 `price-request.schema.json`；可直接试用 `ai_service/price_predict/examples/price-request.json`。

必需字段：city、district、community_name、building_area_sqm、rooms、halls、bathrooms、floor_level、total_floors、orientation、decoration、has_elevator、property_usage，以及 schema_version、request_id。ID 用字符串传输；面积单位平方米；电梯传 true/false/null。朝向为数组，UNKNOWN 只能独占数组。未知室厅卫或总楼层不能写 0 代替，缺少关键数值先提示用户补充。

板块 business_area 建议填写，小区样本不足时用于同板块参考。其他选填字段与空值见 Schema；经纬度必须成对提供，坐标体系未知。不需要传挂牌总价、挂牌单价、用户身份、客户预算、卖点文本；额外字段会被拒绝。挂牌日期可用于审计，目前不作为预测特征。

支持杭州普通住宅。district 可传“滨江”或“滨江区”，小区只统一括号字符和来源列表外壳，不自动合并别名。传入的数据需与数据库现有字段统一，由 Java 映射英文接口字段，D 不新建业务表。

## 正常结果与无法估价

正常业务响应 HTTP 200，结构为 `{code:0,msg,request_id,data}`，其中：

- `status=DEMO_ONLY`：历史参考演示，可展示参考单价、总价和总价区间。estimated_unit_price_yuan_sqm 单位元/㎡，estimated_total_price_yuan 和区间上下限单位元；页面如展示万元需除以 10000。
- `status=UNAVAILABLE`：无可靠覆盖，单价、总价、区间为 null；展示“无法自动估价，请人工核验”，不显示 0 元，也不把空区间补成固定百分比。
- `price_interval`：lower_total_price_yuan、upper_total_price_yuan、nominal_coverage=0.9、method、calibration_group、calibration_sample_count。0.9 是名义历史校准目标，不是这套房子的成交概率。
- `model_version`、`data_version`：模型与原始数据版本；保存结果时一并记录。
- `usage=HISTORICAL_DEMO`，`price_basis=LISTING_UNVERIFIED`，`data_observed_at=null`：数据采集时点未知，不使用生成时间冒充价格观测时间。
- `comparable_sample_count`、`reference_level`：训练中同小区或同板块记录数，不能展示为严格匹配的真实成交案例数量。
- `quality_flags`、`explanations`、`notice`：质量提示与说明；前端应展示历史参考提示。

真实接口响应示例位于 `examples/price-response.json` 和 `examples/unavailable-response.json`；generated_at 是生成示例时的时间。

无法估价原因包括 CITY_NOT_SUPPORTED、PROPERTY_USAGE_NOT_SUPPORTED、DISTRICT_NOT_SUPPORTED、INSUFFICIENT_REFERENCE_SAMPLES、INSUFFICIENT_CALIBRATION_SAMPLES、OUT_OF_TRAINING_RANGE:字段、INVALID_MODEL_OUTPUT。理由可能多个。正常参考提示包括 HISTORICAL_DATA、PRICE_BASIS_UNVERIFIED、BUSINESS_AREA_FALLBACK、MISSING_OPTIONAL_FEATURES；有提示不等于请求失败，应以 status 判断是否可展示价格。

## 异常与探活

HTTP 422 / code 42201：请求结构、枚举或跨字段校验失败，data=null。HTTP 503 / code 50301：模型尚未就绪。HTTP 500 / code 50001：服务内部错误。GET /health 检查进程，GET /ready 检查模型加载状态。

Java 可先设置连接超时 2 秒、读取超时 10 秒，根据联调调整。超时、非 200 响应或网络故障应向页面显示“估价服务暂不可用”，不得回填 0 元。Java 可按团队公共 Result 结构包装 Python 返回，但需保留 data.status、空值、版本和提示。

## 保存与页面行为

预测记录字段参考 `给数据库同学的AI字段清单.md`。每次保存输入快照与返回结果，不覆盖业主挂牌价。人工建议价另行保存，不自动当作真实成交标签。房源关键特征变化后旧估价应提示过期，可重新调用。

页面按钮可命名“历史样本参考估价”。展示单价、总价、区间及免责声明；不要写“未来房价预测”“90% 成交概率”或“权威市场估值”。采用建议价格应由经纪人确认。

## 当前模型边界

本模型基于现有杭州历史二手房文件，价格口径与采集时间未核实，没有新增外部数据。90% 参考区间来自本次拟合之外的历史校准样本；日期依赖及反复使用旧测试数据，使其不能获得当前/未来市场覆盖保证。参考测试可估价样本区间覆盖率 90.73%，拒绝率 2.03%；该结果仅用于了解历史实验表现。
