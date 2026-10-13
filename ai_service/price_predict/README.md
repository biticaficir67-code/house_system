# D 模块：历史房源辅助定价服务

本目录是交付给项目的独立 Python 服务。Spring Boot 通过 HTTP 调用，Vue3 通过 Spring Boot 获取结果。D 不需要编写 Java 或 Vue 业务代码。

## 文件用途

- `start_service.py`：启动入口，默认地址 `127.0.0.1:8001`。
- `api.py`：FastAPI 接口、健康检查和错误处理。
- `price_reference.py`：请求校验、参考价格区间、无法估价提示。
- `features.py`、`modeling.py`：特征处理及已保存模型反序列化需要的类；不要只拷贝模型文件而遗漏它们。
- `models/reference.joblib`：本地已训练的最终区间服务模型，约 2.8 MB，供推理使用。
- `models/manifest.json`：模型来源、校准结果、依赖版本和文件校验值。
- `schemas/price-request.schema.json`：运行时请求规约；项目 `docs/ai` 中有相同内容供团队查阅。
- `examples/`：联调请求、实际响应示例。
- `tests/`：服务测试；`requirements-dev.txt` 用于测试环境。
- `vendor/`：当前电脑的临时 LightGBM 依赖副本，不提交 Git；其他电脑安装 requirements.txt。

训练迭代、原实验脚本、全部评估报告与旧模型已移到工作区的 `ai_price_iterations/ai_service/price_predict`，不属于部署目录。

## 启动

从项目根目录执行，当前电脑已有依赖，可直接启动：

```powershell
& 'D:\python\anaconda\python.exe' ai_service/price_predict/start_service.py
```

其他电脑建议使用 Python 3.12，创建独立环境并执行 `python -m pip install -r ai_service/price_predict/requirements.txt`，然后 `python ai_service/price_predict/start_service.py`。模型按这里固定的依赖版本验证，避免随意升级 scikit-learn 后加载旧模型。

若 Java 与 Python 部署在不同机器，可按部署需要设置 `--host 0.0.0.0 --port 8001`。Java 的调用地址使用 Python 所在机器的内部地址。`PRICE_MODEL_PATH` 环境变量可指定另一个模型文件，默认使用本目录 models/reference.joblib。

接口：POST `/ai/predict/price`，GET `/health`，GET `/ready`。启动后访问 `http://127.0.0.1:8001/docs` 可查看接口页面；准确字段枚举以 JSON Schema 和项目对接说明为准。

本地联调：

```powershell
Invoke-RestMethod -Uri 'http://127.0.0.1:8001/ai/predict/price' -Method Post -ContentType 'application/json; charset=utf-8' -Body ([System.IO.File]::ReadAllText((Resolve-Path 'ai_service/price_predict/examples/price-request.json')))
```

测试：`python -m unittest discover -s ai_service/price_predict/tests -p 'test_*.py' -q`。

## 数据与效果

保持既有 LightGBM 参数；12572 条拟合，2253 条独立于本次拟合的校准记录。历史参考测试 3204 条中可估价 3139 条，拒绝 65 条，可估价区间实际覆盖率 90.73%；全体点预测 MAPE 8.5485%。旧测试曾用于其他实验，此次不是新的独立效果证明。

返回 `DEMO_ONLY` 的结果仅是历史样本辅助定价演示；`UNAVAILABLE` 时单价、总价、区间均为空，提示人工核验。数据采集时间与挂牌/成交价格口径未核实，不代表当前市场价或未来预测。名义 90% 区间不保证覆盖实际成交价。后端与前端对接、数据库字段见项目 `docs/ai`。
