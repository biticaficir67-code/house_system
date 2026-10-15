# 房源系统后端

当前后端基于 Java 17、Spring Boot 4.1.1、MyBatis 和 Maven。首个业务模块是房源管理，覆盖新增、详情、分页/筛选、编辑、删除和状态流转；本目录只放 Java 后端代码，不包含前端工程。

## 启动

默认配置使用内存 H2 数据库，启动时自动创建 `sys_user`、`owner`、`house`、`customer`、`house_show` 五张表，适合本地开发和课程演示。

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

如果电脑已安装 Maven，也可以执行 `mvn spring-boot:run`。服务默认地址为 `http://localhost:8080`。健康检查：

```powershell
Invoke-RestMethod http://localhost:8080/api/health
```

## 使用 MySQL

创建 `house_system` 数据库后，用 mysql profile 启动。连接信息支持环境变量覆盖：

```powershell
$env:DB_URL = 'jdbc:mysql://localhost:3306/house_system?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
$env:DB_USERNAME = 'root'
$env:DB_PASSWORD = '你的数据库密码'
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=mysql'
```

MySQL profile 启动时执行 `src/main/resources/db/schema-mysql.sql`。如需调整默认配置，可编辑 `src/main/resources/application.yml`。

当前预留的 AI 服务配置对应仓库中已有的 Python 服务：

- 文案分析：`AI_TEXT_ANALYZE_URL`，默认 `http://127.0.0.1:5001/ai/analyze`
- 历史价格参考：`AI_PRICE_PREDICT_URL`，默认 `http://127.0.0.1:8001/ai/predict/price`

这两个地址作为对接配置保留；当前房源模块不依赖 Python 服务也能启动。

## 房源接口

所有响应采用 `{ "code": 0, "msg": "ok", "data": ... }`。错误响应使用相同外层结构。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | `/api/health` | 服务健康状态 |
| GET | `/api/houses?page=1&size=10&keyword=德信&status=ON_SALE` | 分页、关键词和状态筛选 |
| GET | `/api/houses/{id}` | 查询房源详情 |
| POST | `/api/houses` | 新增房源，初始状态为 `PENDING_REVIEW` |
| PUT | `/api/houses/{id}` | 更新房源资料 |
| PATCH | `/api/houses/{id}/status` | 流转房源状态 |
| DELETE | `/api/houses/{id}` | 删除房源 |

新增/编辑请求示例：

```json
{
  "title": "金沙湖精装三居室",
  "city": "杭州",
  "district": "钱塘",
  "businessArea": "金沙湖",
  "communityName": "德信早城",
  "buildingAreaSqm": 127.27,
  "rooms": 3,
  "halls": 2,
  "bathrooms": 2,
  "floorLevel": "LOW",
  "totalFloors": 22,
  "orientation": ["S"],
  "decoration": "FINE",
  "hasElevator": true,
  "propertyUsage": "RESIDENTIAL",
  "listingPriceYuan": 3200000,
  "coreSelling": "近地铁，精装修",
  "communityIntro": "人车分流，物业服务较好",
  "surrounding": "周边有商场和医院"
}
```

状态请求：`PATCH /api/houses/1/status`，请求体 `{"status":"ON_SALE"}`。支持 `PENDING_REVIEW`、`ON_SALE`、`OFF_SALE`、`SOLD`，服务会校验允许的流转路径。

创建房源不要求可选字段填满；面积、城市、小区、标题和户型数量等必填项由后端校验。CORS 仅开放本地 Vite 开发地址 `localhost:5173` 和 `127.0.0.1:5173`。

## 目录

- `src/main/java/cn/csu/housesystem/common`：统一响应、业务异常和异常转换
- `src/main/java/cn/csu/housesystem/config`：Web/CORS 和健康检查
- `src/main/java/cn/csu/housesystem/house`：房源接口、服务、MyBatis Mapper 和模型
- `src/main/resources/application.yml`：本地 H2、MySQL profile、服务地址配置
- `src/main/resources/db`：本地 H2 和 MySQL 建表脚本

登录鉴权、业主/客源/带看业务接口、文件上传、AI HTTP 调用和统计报表不在当前第一版模块内。
