# 价格预测接入

入口：房源管理 → 价格建议，路由 `/price-advice/:houseId`。沿用组内 NavMain、Axios 登录拦截器和金色主题。

前端只访问 Java：

- `GET /api/houses/{id}`：当前房源。
- `POST /api/ai/houses/{id}/price-predictions`：以服务端已保存房源特征发起预测，不提交挂牌价作为模型输入。
- `GET /api/ai/houses/{id}/price-predictions`：最多 100 条历史记录，含输入快照和 stale 标记。

Java 验证账号与房源权限，再调用 Python `POST /ai/predict/price`，保存预测记录与请求日志。默认 Python 地址 `http://127.0.0.1:8001`，可通过后端 `AI_PRICE_BASE_URL` 配置。

本机启动模型：

```powershell
& 'D:\python\anaconda\python.exe' ai_service/price_predict/start_service.py
```

其他电脑按 `ai_service/price_predict/README.md` 安装固定版本依赖和模型文件。就绪检查 `GET /ready`，Python 服务应仅通过可信内部网络供后端访问。

页面区分 DEMO_ONLY、UNAVAILABLE、FAILED 及旧导入记录。仅 DEMO_ONLY 展示正数价格和有效区间；其余状态不将空价格显示为零。不修改挂牌价，不将历史参考称为当前市场价或正式评估。资料变更后的历史预测显示过期提醒。重复点击在请求期间禁用，切换房源时丢弃旧页面请求结果。

测试：`npm run test:pricing`；构建：`npm run build`。模型测试见 Python 服务 README。实际联调已覆盖参考价格返回、预测落库、挂牌价不变、不支持物业类型返回空价格、经纪人跨房源权限拒绝。
