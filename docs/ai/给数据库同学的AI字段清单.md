# 给数据库同学的 AI 字段清单

范围：D 的第一版杭州普通住宅历史价格参考模型，只使用现有杭州 CSV。以下是字段建议，不是建表迁移；字段已存在时复用，由 A/C 决定实际表名和列名。Java 发给 Python 时统一映射为以下英文名。

## 一、房源表需要提供的模型输入

### 第一版必需

1. `id`：系统房源主键，BIGINT。用于关联预测记录，不参与训练；未保存房源也能估价，因此预测请求的 house_id 可空。ID 对外以字符串传输。
2. `city`：城市，VARCHAR(32)。第一版为杭州。
3. `district`：行政区域，VARCHAR(32)。对应原“区域位置”前半段，例如滨江；临平区/钱塘区标准化为临平/钱塘。
4. `community_name`：小区名称，VARCHAR(128)。对应原“小区名称”，去掉列表形式的外壳，保留完整名称。
5. `building_area_sqm`：建筑面积，DECIMAL(10,2)。单位平方米，必须大于 0；对应原“建筑面积”。
6. `rooms`：室数，SMALLINT。由“房屋户型”解析，正整数。
7. `halls`：厅数，SMALLINT。由“房屋户型”解析，非负整数；未知不是 0。
8. `bathrooms`：卫生间数，SMALLINT。由“房屋户型”解析，非负整数；未知不是 0。
9. `floor_level`：所在楼层档位，VARCHAR(16)。LOW=低楼层、MIDDLE=中楼层、HIGH=高楼层、UNKNOWN=未知。对应原“所在楼层”。
10. `total_floors`：楼栋总楼层，SMALLINT。由原“所在楼层”的“共18层”等解析，正整数。
11. `orientation`：朝向，JSON。API 用数组，例如 ["S","N"] 表示南、北；取值 E/S/W/N/NE/NW/SE/SW/UNKNOWN。不能把东南拆成东+南；UNKNOWN 不与其他方向并存。
12. `decoration`：装修情况，VARCHAR(16)。ROUGH=毛坯、SIMPLE=简装、FINE=精装、OTHER=其他、UNKNOWN=未知。对应原“装修情况”。
13. `has_elevator`：是否有电梯，TINYINT，可空。有=1、无=0、未知=NULL。API 转为 true/false/null。
14. `property_usage`：房屋用途，VARCHAR(32)。RESIDENTIAL=普通住宅、MIXED_USE=商住两用、VILLA=别墅、GARAGE=车库、UNKNOWN=未知。首版只估普通住宅。

“模型必需”不等于业务表每列都必须 NOT NULL。业务允许先保存不完整房源；缺少模型必需信息时，由后端提示补充后再调用估价。floor_level、orientation、decoration 可以明确传 UNKNOWN，电梯可以传 null，模型记录缺失提示。

### 建议提供，允许为空

- `business_area`：板块，VARCHAR(128)。原“区域位置”后半段，例如滨江区政府；用于小区样本不足时寻找同板块参考。
- `community_id`：已有小区主键，BIGINT/外键。用于统一小区别名；不作为数值模型特征。
- `longitude`、`latitude`：经纬度，各 DECIMAL(10,6)。原“经度”“纬度”；成对提供，坐标体系未知，不据此直接计算地铁距离。
- `building_type`：建筑类型，VARCHAR(32)。SLAB=板楼、TOWER=塔楼、SLAB_TOWER=板塔结合、BUNGALOW=平房、UNKNOWN=未知。
- `layout_structure`：户型结构，VARCHAR(32)。FLAT=平层、DUPLEX=跃层/复式、SPLIT_LEVEL=错层、LOFT、OTHER、UNKNOWN；保留原始文本以便复核。
- `construction_structure`：建筑结构，VARCHAR(32)。STEEL_CONCRETE=钢混、BRICK_CONCRETE=砖混、MIXED=混合、FRAME=框架、STEEL=钢、BRICK_WOOD=砖木、UNKNOWN=未知。
- `ownership_type`：交易权属，VARCHAR(32)。COMMERCIAL=商品房、PUBLIC_PURCHASED=已购公房、RELOCATION=回迁房、PRIVATE=私产、UNKNOWN=未知。
- `internal_area_sqm`：套内面积，DECIMAL(10,2)。平方米，可空，若有值应大于 0 且不大于建筑面积。现有包只有约36.42%行有可用值，不设为必填。
- `listing_date`：挂牌日期，DATE。只用于审计，不当作价格采集日期。
- `listing_price_yuan`：业主挂牌价，DECIMAL(16,2)。业务已有则复用；用于报价对比，不输入首版模型。

## 二、离线训练数据需要保留的字段

此部分由 D 在离线清洗数据中维护，不要求全部添加到在线房源表，更不能写入真实成交表。

- `sample_unit_price_yuan_sqm`：源“单价”，元/平方米，首版训练目标 y。
- `sample_total_price_yuan`：源“总价”乘10000，元，用于一致性检查和总价误差评估。
- `source_listing_id`：源链家编号，字符串，用于去重；不是系统房源 id。
- `source_dataset_id`、`source_file`、`source_row`：数据批次、文件、行号，用于追溯。
- `source_price_basis`：固定 LISTING_UNVERIFIED，表示疑似挂牌、未核验。
- `observed_at`：价格观测时间，现有文件未知，保留 NULL。
- `cleaning_version`、`source_archive_sha256`、`quality_flags`：清洗版本、原包哈希、质量问题。

总价、单价及由价格计算的字段不能同时作为模型输入。现有数据没有可核验的成交价格，因此本阶段不为 D 新建 house_transaction 成交样本表。

## 三、建议新增预测结果表 house_price_pred

每次估价单独留记录，不覆盖业主挂牌价。

- `id`：BIGINT，预测记录主键。
- `house_id`：BIGINT，关联房源；尚未保存时可暂不落库，或允许空并在房源保存后关联，由 A 决定。
- `request_id`：VARCHAR(128)，请求追踪编号。
- `requested_by`：BIGINT，发起用户，由 Java 认证身份填充。
- `schema_version`：VARCHAR(16)，接口字段版本。
- `input_snapshot`：JSON，当次房源特征快照。
- `feature_hash`：VARCHAR(64)，由规范化输入计算的哈希，用于判断特征是否变化，不是模型特征。
- `status`：VARCHAR(32)，DEMO_ONLY=历史参考价、UNAVAILABLE=无法估价。
- `usage`：VARCHAR(32)，首版 HISTORICAL_DEMO。
- `price_basis`：VARCHAR(32)，首版 LISTING_UNVERIFIED。
- `estimated_unit_price_yuan_sqm`：DECIMAL(14,2)，参考单价，元/平方米，可空。
- `estimated_total_price_yuan`：DECIMAL(16,2)，参考总价，元，可空。
- `price_interval`：JSON，可空。当前服务可估价时返回总价上下限（元）、名义覆盖目标、校准方法与校准样本数；UNAVAILABLE 时为空。
- `reference_level`：VARCHAR(32)，当前服务仅返回 COMMUNITY/BUSINESS_AREA/NONE，不使用全区均价强制兜底。
- `comparable_sample_count`：INT，所选参考层级下去重合格样本数量；不是置信度。
- `quality_flags`、`explanations`：JSON，质量提示与解释。
- `model_version`、`data_version`：VARCHAR(64)，模型与数据版本。
- `data_observed_at`：DATETIME，可空，现有数据为 NULL，不能用预测时间填充。
- `generated_at`：DATETIME，预测生成时间；项目数据库统一时区，API 使用带时区时间。
- `notice`：TEXT，历史样本、未核验价格口径等提示。

UNAVAILABLE 时价格/区间为 NULL，不存成 0。房源输入修改后，后端通过快照/哈希判断旧预测过期。人工建议价如需保存另设 manual_suggested_price_yuan 等字段，不覆盖 AI 原结果，也不作为真实训练标签。

## 四、复用现有卖点模块的三列

只保留 `core_selling`（核心卖点）、`community_intro`（小区介绍）、`surrounding`（周边配套），建议 TEXT；调用已有接口时前两项分别映射 coreSelling、communityIntro。为该模块不新增适宜人群、税费解析、交通出行等业务字段。

这三段文本不输入首版价格模型。原始 CSV 已发现部分语义错位，原文先离线保留并核验，不能直接把列名当成已核验业务内容。

## 五、第一版不要求新增的字段

不要求建造年份、精确所在楼层、顶楼标志、地铁距离、学区、片区涨幅、近三个月真实成交均价、客户成交概率。这些在现有文件中没有可靠结构化数据。

“房屋年限”的满五年不是建筑年龄，“上次交易”不是本次挂牌成功成交，“关注度”不是带看次数或客户意向。

当前接口、空值与错误处理见 `价格预估API对接说明.md`；机器可读请求字段见 `price-request.schema.json`。旧版数据规约与需求讨论已归档到工作区 ai_price_iterations/docs/ai，当前联调以本目录文档和实际服务为准。
