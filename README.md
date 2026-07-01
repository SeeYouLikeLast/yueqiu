# 球动 ShuttleMate

仿照 HMDP 黑马点评思路实现的球类生活服务项目，当前覆盖羽毛球、乒乓球、足球、篮球、网球、排球等运动。AI 应用层暂未实现，其他核心业务已落地。

## 功能

- HMDP 式手机号验证码登录：Redis 保存验证码和登录 token，未注册手机号验证后自动创建用户。
- 注册唯一性：Redis 布隆过滤器预判 + MySQL 唯一索引兜底。
- 球类运动目录：按 `sportCode` 切换运动类型。
- 附近场所：接入高德 Web 服务 POI 周边搜索，首页不再展示本地虚构场馆。
- 手机版首页：按美团式移动端结构展示定位、搜索、分类、附近可订场所和底部导航。
- 场所交易：支持单人畅打、单场时段、私教课售卖、库存扣减、下单、支付模拟和核销码。
- 本地场馆元数据：保留场地时段、评价、收藏、后台管理能力。
- 装备商城：按运动类型过滤分类和商品，支持购物车、普通订单、支付模拟。
- 装备秒杀：Redis Lua 原子预扣库存、一人一单、数据库唯一索引兜底，Redis 不可用时降级数据库扣减。
- 球友社区：仿 HMDP 关注、点赞与 Feed 推流，博客可关联装备商品或场所售卖项目。
- 附近球友、同城约球、发起活动、加入活动。
- 文件存储：MySQL 保存文件元数据，MinIO 保存文件本体。
- 后台统计、热门评价场所、热卖装备、场馆 / 商品新增接口。

## 技术栈

- 后端：Java 21、Spring Boot 3.5、MyBatis-Plus、Spring JDBC、Spring Data Redis。
- 数据：MySQL 8.4、Redis 7.4、MinIO。
- 地图：高德 Web 服务 API。
- 前端：Vue 3、TypeScript、Vite、lucide-vue-next。
- 部署：Docker Compose 提供 MySQL / Redis / MinIO。

## 目录结构

```text
hm-badminton/
├─ backend/              # Spring Boot 后端
│  └─ src/main/java/com/hm/badminton/
│     ├─ controller/     # HTTP 接口层
│     ├─ service/impl/   # 业务实现层
│     ├─ mapper/         # 预留 MyBatis / MyBatis-Plus Mapper
│     ├─ entity/         # 实体 / 领域数据对象
│     ├─ dto/            # 请求 / 内部传输对象
│     ├─ vo/             # 前端展示对象
│     ├─ config/         # Spring 与第三方配置
│     ├─ common/         # 通用响应、异常、分页
│     ├─ utils/          # 工具类、上下文、ID 生成
│     ├─ interceptor/    # 拦截器
│     └─ constants/      # 常量
├─ frontend/             # Vue 前端
├─ deploy/               # Docker Compose 与中间件配置
├─ docs/                 # 环境与项目文档
├─ scripts/              # 简单压测 / 冒烟脚本
└─ README.md
```

## 环境文档

WSL + Docker 环境搭建见：

```text
docs/WSL-Docker-MySQL-Redis-环境配置.md
```

后端 HMDP 分层结构与 `/api` 转发规则见：

```text
docs/后端HMDP分层结构与API转发.md
```

MySQL / Redis / MinIO 数据分工见：

```text
docs/数据存储分工-MySQL-Redis-MinIO.md
```

当前默认端口：

```text
MySQL: localhost:3307
Redis: localhost:6379
MinIO API: localhost:9000
MinIO Console: localhost:9001
后端: http://localhost:8088
前端: http://localhost:5173
```

MinIO 控制台：

```text
地址: http://localhost:9001
账号: minioadmin
密码: minioadmin123
Bucket: hm-badminton
```

`deploy/.env` 默认配置镜像地址、MinIO 账号和 bucket。

## 启动步骤

1. 在 WSL / Ubuntu 中启动 MySQL、Redis、MinIO：

```bash
cd /mnt/d/Program/java/hm/hm-badminton/deploy
docker compose up -d
docker ps
```

2. 在 Windows PowerShell 启动后端：

```powershell
cd D:\Program\java\hm\hm-badminton\backend
mvn spring-boot:run
```

3. 在 Windows PowerShell 启动前端：

```powershell
cd D:\Program\java\hm\hm-badminton\frontend
npm install
npm run dev
```

开发环境中前端请求 `/api/xxx`，Vite proxy 会剥离 `/api` 后转发到后端。直接访问后端 `8088` 时不带 `/api`：

```text
前端开发服务器：http://localhost:5173/api/sports
后端直连：http://localhost:8088/sports
```

访问：

```text
http://localhost:5173
```

## 测试账号

所有模拟账号密码都是：

```text
123456
```

可用账号：

```text
13800000001
13800000002
13800000003
chen@example.com
chenyu
```

## 重要配置

后端配置文件：

```text
backend/src/main/resources/application.yml
```

数据库初始化：

```text
backend/src/main/resources/db/schema.sql
backend/src/main/resources/db/data.sql
```

当前开发配置会在后端每次启动时重新执行 schema 和 data，方便学习和调试。正式环境需要把 `spring.sql.init.mode` 改为 `never`，并使用 Flyway / Liquibase 做版本化迁移。

高德 Web 服务 Key：

```yaml
hm:
  amap:
    key: ${AMAP_KEY:}
```

Windows PowerShell 临时配置：

```powershell
$env:AMAP_KEY="你的高德Web服务Key"
```

WSL / Linux 临时配置：

```bash
export AMAP_KEY="你的高德Web服务Key"
```

未配置 Key 时，`/api/places/nearby` 会直接返回配置提示，不会用本地假场馆冒充真实搜索结果。

## 文件存储

文件采用：

```text
MySQL: file_metadata 表保存元数据
MinIO: hm-badminton bucket 保存文件本体
```

元数据字段包括：

```text
owner_user_id
biz_type
biz_id
bucket_name
object_name
original_filename
content_type
file_size
etag
public_url
status
```

上传示例：

```powershell
curl.exe -X POST "http://localhost:8088/files/upload?bizType=venue&bizId=1" `
  -F "file=@D:\test\court.jpg"
```

## 关键接口

下面列的是前端 / Nginx 对外路径，保留 `/api` 前缀；如果直接访问后端 `http://localhost:8088`，需要去掉 `/api`。

认证：

```text
POST /api/auth/register
POST /api/auth/login
GET  /api/auth/me
```

运动类型：

```text
GET  /api/sports
```

附近真实场所：

```text
GET  /api/places/nearby?sport=badminton&city=西安&lng=108.946465&lat=34.347269
```

本地场馆元数据 / 评价：

```text
GET  /api/venues
GET  /api/venues/{id}
GET  /api/venues/{id}/courts
GET  /api/venues/{id}/slots
GET  /api/venues/{id}/reviews
POST /api/venues/reviews
POST /api/venues/{id}/favorite
```

场所售卖 / 订场：

```text
GET  /api/venue-products/sales
GET  /api/venue-products?sport=badminton&amapPlaceId=B0LBZ5OK5J
GET  /api/venue-products/{id}/inventories
POST /api/venue-products/{id}/quick-pay
POST /api/venue-orders
POST /api/venue-orders/{id}/pay
GET  /api/venue-orders/me
```

装备：

```text
GET    /api/equipment/categories?sport=badminton
GET    /api/equipment/products?sport=badminton
GET    /api/equipment/products/{id}
POST   /api/equipment/cart
GET    /api/equipment/cart
DELETE /api/equipment/cart/{itemId}
POST   /api/equipment/orders
POST   /api/equipment/orders/{orderId}/pay
GET    /api/equipment/orders
```

秒杀：

```text
GET  /api/seckill/activities
POST /api/seckill/activities/{activityId}/orders
GET  /api/seckill/orders
GET  /api/seckill/orders/{orderId}
```

约球：

```text
GET  /api/social/players?sport=badminton
GET  /api/social/profile/me
POST /api/social/profile/me
GET  /api/social/activities?sport=badminton
POST /api/social/activities
POST /api/social/activities/{id}/join
POST /api/social/activities/{id}/cancel
```

文件：

```text
POST   /api/files/upload
GET    /api/files
GET    /api/files/{id}
GET    /api/files/{id}/download
GET    /api/files/{id}/presigned-url
DELETE /api/files/{id}
```

后台：

```text
GET  /api/admin/overview
GET  /api/admin/hot-venues
GET  /api/admin/hot-products
POST /api/admin/venues
POST /api/admin/products
```

## 秒杀说明

秒杀请求先执行 Redis Lua：

```text
校验库存 > 0
校验用户没有购买过
扣减 Redis 库存
写入用户购买集合
```

然后写入 MySQL 秒杀订单，`uk_seckill_user_activity` 唯一索引兜底防重复。Redis 不可用时，服务会使用数据库条件扣减：

```sql
update seckill_activities
set stock = stock - 1
where id = ? and stock > 0
```

第一版没有接入真实 MQ，后续可以把 `SeckillService#createSeckillOrder` 改为发送 RocketMQ / Kafka 消息，再由消费者异步落库。

## 开发种子数据

`db/data.sql` 仍保留开发种子数据，用于商城、秒杀、约球、后台、评价和本地元数据调试。首页附近场所不读取这些虚构场馆，而是调用高德 POI。

- 6 个本地场所元数据，覆盖多种球类。
- 6 个场所售卖项目：单人畅打、单场时段、私教课。
- 10 条场所商品库存。
- 14 个装备商品。
- 3 个秒杀活动。
- 5 个球友资料。
- 4 个约球活动。
- 评价、收藏、购物车、订单示例。

高德官方文档：

```text
Web 服务概览: https://lbs.amap.com/api/webservice/summary
POI 搜索 2.0: https://lbs.amap.com/api/webservice/guide/api-advanced/newpoisearch
```
