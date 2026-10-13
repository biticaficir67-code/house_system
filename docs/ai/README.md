# D 模块交付入口

后端 A 首先阅读 [价格预估API对接说明.md](价格预估API对接说明.md)，再参考 [给数据库同学的AI字段清单.md](给数据库同学的AI字段清单.md)。机器可读请求字段见 [price-request.schema.json](price-request.schema.json)。前端 B 重点阅读对接说明中的结果状态、价格单位和页面行为。

Python 代码、已训练模型、启动方式及联调示例位于 `ai_service/price_predict`。此模块由 D 维护。旧训练迭代和研究文档已移到工作区的 `ai_price_iterations`，不需要部署到 Spring Boot 或 Vue3 中。
