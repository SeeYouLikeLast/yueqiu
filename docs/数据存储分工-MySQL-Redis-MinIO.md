# 数据存储分工：MySQL / Redis / MinIO / RocketMQ

本文档是项目存储设计的维护入口。后续新增表、Redis key、MinIO 目录规则或消息队列 Topic 时，需要同步修改本文件。

当前中间件运行在 WSL Docker 中：

```text
MySQL:    hm-badminton-mysql, 3307 -> 3306
Redis:    hm-badminton-redis, 6379 -> 6379
MinIO:    hm-badminton-minio, 9000 / 9001
RocketMQ: hm-badminton-rocketmq-namesrv, hm-badminton-rocketmq-broker
```

## 存储边界

| 存储 | 存放内容 | 数据特性 |
| --- | --- | --- |
| MySQL | 用户、真实场所、场所商品、装备商品、订单、关注、博客、评价、文件元数据 | 强一致、可索引、需要事务兜底 |
| Redis | 登录 token、验证码、布隆过滤器、秒杀库存、秒杀用户集合、关注集合、点赞集合、Feed 收件箱 | 高频读写、短生命周期或可重建 |
| MinIO | 上传文件本体，例如头像、评价图片、博客图片、附件 | 大对象、二进制、非结构化 |
| RocketMQ | 秒杀订单创建消息 | 削峰、异步落库、可重试 |

核心原则：

- MySQL 保存业务真实数据和最终结果。
- Redis 只保存临时态、缓存态、可重建状态，以及高并发场景下的预判断数据。
- MinIO 只保存文件本体，文件归属、业务类型、URL 等元数据保存到 MySQL `file_metadata`。
- RocketMQ 负责秒杀请求削峰，订单最终仍落到 MySQL。

## MySQL 表

数据库：`hm_badminton`

```text
Host: 127.0.0.1
Port: 3307
User: root
Password: 123456
Database: hm_badminton
```

主要表：

| 表 | 说明 |
| --- | --- |
| `users` | 用户账号、手机号、昵称、头像、城市、水平 |
| `follows` | 用户关注关系 |
| `blogs` | 球友社区动态，可关联 `equipment` 或 `venue` |
| `place` | 真实场所信息，即真实球馆/球场。首页附近场所主要来自高德 API，本表保存本地场所和评价关联对象 |
| `venue` | 场馆商品/场所售卖项目，例如单人畅打、单场时段、私教课 |
| `venue_inventory` | 场馆商品的具体日期、时间、场地/教练、库存 |
| `order_venue` | 普通场馆商品订单 |
| `equipment_categories` | 装备分类 |
| `equipment` | 普通装备商品 |
| `cart_equipment` | 装备购物车 |
| `order_equipment` | 普通装备订单主表 |
| `order_equipment_item` | 普通装备订单明细 |
| `seckill_venue` | 场馆商品秒杀活动 |
| `order_seckill_venue` | 场馆商品秒杀订单 |
| `seckill_equipment` | 装备秒杀活动 |
| `order_seckill_equipment` | 装备秒杀订单 |
| `venue_reviews` | 场所评价 |
| `venue_favorites` | 场所收藏 |
| `player_profiles` | 球友资料 |
| `sport_activities` | 约球活动 |
| `sport_activity_members` | 约球活动成员 |
| `file_metadata` | MinIO 文件元数据 |

命名边界：

- `place` 表示真实场所。
- `venue` 表示场馆商品，不再表示真实场馆。
- 普通订单分为 `order_venue` 和 `order_equipment`。
- 秒杀活动分为 `seckill_venue` 和 `seckill_equipment`。
- 秒杀订单分为 `order_seckill_venue` 和 `order_seckill_equipment`。

开发环境当前配置了：

```yaml
spring:
  sql:
    init:
      mode: always
```

因此后端重启会执行 `schema.sql` 和 `data.sql`，并重置 MySQL 数据。需要保留真实数据时改为 `mode: never`。

## 统一业务 API

前端仍统一通过 `/api` 访问，Vite/Nginx 会去掉 `/api` 后转发给后端。

```text
GET  /api/items/1                 场馆商品列表
GET  /api/items/2                 装备商品列表
GET  /api/items/1/{id}            场馆商品详情
GET  /api/items/2/{id}            装备商品详情
GET  /api/items/1/{id}/inventories

GET  /api/categories/1            场馆商品分类
GET  /api/categories/2            装备分类

POST /api/orders/1                创建/快速支付场馆商品订单
POST /api/orders/2                创建装备订单
POST /api/orders/1/{orderId}/pay
POST /api/orders/2/{orderId}/pay
GET  /api/orders/1                我的场馆订单
GET  /api/orders/2                我的装备订单
GET  /api/orders/all              我的普通订单汇总

GET  /api/seckill/1               场馆秒杀列表
GET  /api/seckill/2               装备秒杀列表
POST /api/seckill/1/{seckillId}   场馆秒杀下单
POST /api/seckill/2/{seckillId}   装备秒杀下单
GET  /api/seckill/1/orders        我的场馆秒杀订单
GET  /api/seckill/2/orders        我的装备秒杀订单
GET  /api/seckill/all/orders      我的秒杀订单汇总
```

旧的 `/venue-products`、`/venue/orders`、`/equipment/orders`、`/seckill/activities` 仍可作为兼容入口保留，但新页面优先使用统一 API。

## Redis Key

| Key 模式 | 类型 | 内容 | TTL |
| --- | --- | --- | --- |
| `login:code:{phone}` | String | 手机验证码 | 2 分钟 |
| `login:token:{token}` | Hash | 登录用户摘要和定位信息 | 120 分钟 + 随机抖动，访问时刷新 |
| `bf:user:phone` | Bitmap/String | 手机号布隆过滤器 | 永久 |
| `bf:user:email` | Bitmap/String | 邮箱布隆过滤器 | 永久 |
| `bf:user:username` | Bitmap/String | 用户名布隆过滤器 | 永久 |
| `seckill:stock:{type}:{seckillId}` | String | 秒杀库存，`type=1` 场馆，`type=2` 装备 | 活动结束后 1 小时 + 随机抖动 |
| `seckill:users:{type}:{seckillId}` | Set | 已参与该秒杀的用户 id | 活动结束后 1 小时 + 随机抖动 |
| `seckill:activity:{type}:{seckillId}` | Hash | 秒杀活动元数据；不存在时写入空值标记防穿透 | 正常：活动结束后 1 小时 + 随机抖动；空值：2 分钟 |
| `equipment:{id}` | String(JSON) | 装备详情；不存在时写入空值防穿透 | 正常：30 分钟 + 随机抖动；空值：2 分钟 |
| `venue:item:{id}` | String(JSON) | 场所售卖项目详情；不存在时写入空值防穿透 | 正常：30 分钟 + 随机抖动；空值：2 分钟 |
| `user:profile:{id}` | String(JSON) | 用户公开主页基础信息；关注/是否本人实时计算 | 正常：30 分钟 + 随机抖动；空值：2 分钟 |
| `blog:{id}` | String(JSON) | 博客基础详情；点赞/关注态实时计算 | 正常：30 分钟 + 随机抖动；空值：2 分钟 |
| `follows:{userId}` | Set | 用户关注的博主 id | 永久，可由 MySQL 重建 |
| `blog:liked:{blogId}` | ZSet | 博客点赞用户，score 为点赞时间 | 永久，可由 MySQL 重建计数 |
| `feed:{userId}` | ZSet | 关注 Feed 收件箱，score 为推送时间 | 永久，可由 MySQL 重建 |

布隆过滤器看起来像 big key 是正常现象：Redis Bitmap 底层是 String，当前 bit size 较大时单个 key 会占用数 MB。它用于注册唯一性预判断，最终仍以 MySQL 唯一索引兜底。

详情缓存采用 Cache Aside + 空值缓存策略：Redis 未命中时查询 MySQL；MySQL 查不到则写入 `__NULL__` 短 TTL 空值，防止恶意请求不存在 id 反复穿透到数据库。库存、点赞等写操作会删除对应详情缓存，避免长时间读取旧数据。

## MinIO

Bucket：

```text
hm-badminton
```

控制台：

```text
http://localhost:9001
账号：minioadmin
密码：minioadmin123
```

MinIO 为空是正常的：`data.sql` 中示例图片多为外链，只有调用 `/api/files/upload` 上传文件后，MinIO 才会出现对象，同时 MySQL `file_metadata` 会写入元数据。

对象路径规则：

```text
{bizType}/{yyyy}/{MM}/{dd}/{uuid}.{suffix}
```

## 查看数据

MySQL：

```bash
docker exec -it hm-badminton-mysql mysql -uroot -p123456 hm_badminton
```

常用 SQL：

```sql
show tables;
select id, phone, nickname from users limit 10;
select * from order_venue order by id desc limit 10;
select * from order_equipment order by id desc limit 10;
select * from order_seckill_venue order by created_at desc limit 10;
select * from order_seckill_equipment order by created_at desc limit 10;
select * from file_metadata order by id desc limit 10;
```

Redis：

```bash
docker exec -it hm-badminton-redis redis-cli
```

常用命令：

```redis
scan 0
hgetall login:token:{token}
ttl login:token:{token}
get seckill:stock:1:1
smembers seckill:users:2:1
strlen bf:user:phone
memory usage bf:user:phone
```

RocketMQ：

```bash
docker exec -it hm-badminton-rocketmq-broker sh mqadmin topicList -n rocketmq-namesrv:9876
```

当前 Topic：

```text
hm-seckill-order
```

秒杀链路：

```text
前端提交 -> Redis Lua 预扣库存并记录用户 -> Redisson 一人一单锁 ->
RocketMQ 投递订单消息 -> Consumer 异步写入 MySQL 秒杀订单
```
