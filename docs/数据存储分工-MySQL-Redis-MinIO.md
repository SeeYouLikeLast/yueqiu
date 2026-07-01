# 数据存储分工：MySQL / Redis / MinIO

本文档是项目存储设计的维护入口。后续如果新增表、Redis key、MinIO 目录规则，或调整数据存储位置，需要同步修改本文档。

当前中间件运行在 WSL Docker 中：

```text
MySQL 容器：hm-badminton-mysql，端口 3307 -> 3306
Redis 容器：hm-badminton-redis，端口 6379 -> 6379
MinIO 容器：hm-badminton-minio，端口 9000 / 9001
RocketMQ NameServer 容器：hm-badminton-rocketmq-namesrv，端口 9876
RocketMQ Broker 容器：hm-badminton-rocketmq-broker，端口 10909 / 10911 / 10912
```

## 1. 存储边界

| 存储 | 存放内容 | 数据特性 | 是否持久化 | 当前作用 |
| --- | --- | --- | --- | --- |
| MySQL | 用户、场所、商品、库存、订单、活动、关注、博客、文件元数据 | 强一致、关系型、需要事务和索引 | 是 | 系统主数据、交易数据与社区内容 |
| Redis | 登录验证码、登录 token、布隆过滤器、秒杀库存和秒杀用户集合、关注集合、博客点赞集合、Feed 收件箱 | 高并发、短生命周期或可重建、读写快 | Docker volume 持久化，但业务上可重建 | 缓存、限流式判断、秒杀预扣、Feed 推流 |
| RocketMQ | 秒杀订单消息 | 交易链路友好、异步削峰、可重试消费、适合后续扩展延迟消息/事务消息 | Docker volume 持久化 | 秒杀请求进入队列后异步创建 MySQL 订单 |
| MinIO | 上传文件本体，例如图片、附件 | 大对象、二进制文件、非结构化 | 是 | 文件对象存储 |

核心原则：

- MySQL 保存业务真实数据和最终结果。
- Redis 保存临时态、缓存态、可重建状态，以及高并发场景下的预判断数据。
- MinIO 只保存文件本体；文件属于谁、业务类型、文件名、访问地址等元数据保存在 MySQL 的 `file_metadata` 表。

## 2. MySQL 存储内容

数据库：`hm_badminton`

连接配置：

```text
Host: 127.0.0.1
Port: 3307
User: root
Password: 123456
Database: hm_badminton
```

主要表：

| 表                         | 存放内容                 | 数据特性                       |
| ------------------------- | -------------------- | -------------------------- |
| `users`                   | 用户账号、手机号、昵称、头像、城市、水平 | 主数据；手机号唯一；登录注册最终以 MySQL 为准 |
| `follows`                 | 用户关注关系                 | 关注关系真实数据；Redis Set 只是加速查询与共同关注 |
| `blogs`                   | 球友社区博客                 | 用户内容；可关联装备商品或场所售卖项目；推荐流与关注流的最终数据源 |
| `venues`                  | 本地场馆元数据              | 结构化场馆信息；当前首页附近场所主要来自高德 API |
| `venue_courts`            | 本地场地信息               | 本地场馆下的场地资源                 |
| `venue_time_slots`        | 本地时段库存               | 本地场地预约时段                   |
| `venue_reviews`           | 场馆评价                 | 用户生成内容，需要关联用户和场馆           |
| `venue_favorites`         | 场馆收藏                 | 用户行为数据                     |
| `coaches`                 | 私教信息                 | 和场所商品、私教课关联                |
| `venue_products`          | 场所售卖项目模板             | 单人畅打、单场时段、私教课等商品定义；用 `place_rank` 绑定附近第 N 个真实场所 |
| `venue_product_inventory` | 场所售卖库存               | 具体日期、时间、场地/教练、剩余库存         |
| `venue_orders`            | 场所订单                 | 下单、支付、核销码，属于交易最终结果         |
| `product_categories`      | 装备分类                 | 按运动类型区分                    |
| `products`                | 装备商品                 | 商品主数据、库存、销量                |
| `cart_items`              | 购物车                  | 用户临时购物数据，但仍需要持久化           |
| `orders`                  | 装备普通订单               | 装备交易主表                     |
| `order_items`             | 装备订单明细               | 装备交易子表                     |
| `seckill_activities`      | 秒杀活动                 | 秒杀商品、价格、活动库存、时间            |
| `seckill_orders`          | 秒杀订单                 | 秒杀最终订单；有唯一索引兜底一人一单         |
| `player_profiles`         | 球友资料                 | 找搭子列表展示数据                  |
| `sport_activities`        | 约球活动                 | 同城约球活动主表                   |
| `sport_activity_members`  | 活动成员                 | 加入约球活动的成员关系                |
| `file_metadata`           | 文件元数据                | 只保存元数据，不保存文件本体             |

首页的“附近可订 / 特价畅打 / 私教课”入口直接读取 MySQL 中的 `venue_products` 与 `venue_product_inventory`，通过 `/venue-products/sales` 分页返回可下单项目；不再通过高德附近场所列表逐个请求售卖项目。高德 API 仍用于首页附近场所发现与地图导航。

场所详情页中的售卖项目采用“序号模板”方案：`venue_products.venue_name` 保存序号字符串，`place_rank` 保存同一个数字；前端点击附近场所时用该场所在当前列表中的顺序请求 `/venue-products?placeRank=N`，再把高德返回的真实店名、`amapPlaceId` 作为下单上下文传给后端。这样用户定位不同也能稳定拿到一组可购买项目。

注意边界：售卖模板数据可以用序号，不应该写死某个高德店名；但 `venue_orders`、`sport_activities` 这类用户行为和交易结果应保存当时的真实店名，方便“我的订单”和约球记录回显。

注意：

```yaml
spring:
  sql:
    init:
      mode: always
```

当前开发配置会在后端启动时执行 `schema.sql` 和 `data.sql`。由于 `schema.sql` 中存在 `drop table if exists`，后端重启会重置 MySQL 数据。后续如果要保留真实数据，应改为：

```yaml
spring:
  sql:
    init:
      mode: never
```

## 3. Redis 存储内容

Redis 当前用于 HMDP 类项目里常见的高并发和登录态场景。

| Key 模式 | 类型 | 存放内容 | TTL | 是否可重建 | 数据特性 |
| --- | --- | --- | --- | --- | --- |
| `login:code:{phone}` | String | 手机验证码 | 2 分钟 | 是 | 短生命周期；用于验证码登录 |
| `login:token:{token}` | Hash | 登录用户摘要：id、phone、nickname、city、level、lng、lat、preciseAddress | 120 分钟 + 0-10 分钟随机抖动，访问时刷新 | 是 | 登录态；类似 HMDP token 登录；位置变化时由 `/auth/location` 更新；随机 TTL 用于降低大量 token 同时过期风险 |
| `bf:user:phone` | String Bitmap | 手机号布隆过滤器 | 无 | 是 | 注册唯一性预判断 |
| `bf:user:email` | String Bitmap | 邮箱布隆过滤器 | 无 | 是 | 注册唯一性预判断 |
| `bf:user:username` | String Bitmap | 用户名布隆过滤器 | 无 | 是 | 注册唯一性预判断 |
| `seckill:stock:{activityId}` | String | 秒杀活动库存 | 活动结束后 1 小时 + 0-30 分钟随机抖动 | 是 | 秒杀库存预热，Lua 原子扣减 |
| `seckill:users:{activityId}` | Set | 已参与该秒杀的用户 id | 活动结束后 1 小时 + 0-30 分钟随机抖动 | 是 | 秒杀一人一单预判断；启动时从 `seckill_orders` 重建 |
| `follows:{userId}` | Set | 当前用户关注的博主 id | 无 | 是 | 关注判断、共同关注查询；MySQL `follows` 可重建 |
| `blog:liked:{blogId}` | ZSet | 给博客点赞的用户 id，score 为点赞时间 | 无 | 是 | 点赞状态判断与按时间扩展查询；MySQL `blogs.liked` 保存计数 |
| `feed:{userId}` | ZSet | 推送给用户的博客 id，score 为推送时间 | 无 | 是 | HMDP 式 Feed 推流收件箱；关注博主发博客时写入 |

### 为什么 Tiny RDM 里 `bf:user:*` 像 big key

截图中的 `bf:user:email`、`bf:user:phone`、`bf:user:username` 是布隆过滤器，不是普通字符串。

当前配置：

```yaml
hm:
  bloom:
    bit-size: 33554432
    hash-count: 7
```

`33554432` 是 bit 数，不是 byte 数。换算后：

```text
33554432 bit = 4194304 byte ≈ 4 MB
```

Redis Bitmap 底层就是 Redis String。只要代码通过 `SETBIT` 写到了靠后的 bit 位，Redis 就会把这个 String 扩展到对应长度。所以 Tiny RDM 会看到：

```text
bf:user:email
类型：STRING
长度：约 4 MB
内存：约 5 MB
```

这是当前布隆过滤器设计导致的预期现象。它不是某个用户数据异常膨胀，也不是业务把大 JSON 塞进了 Redis。

为什么要这样做：

- 注册时先查布隆过滤器。
- 如果布隆过滤器判断“不存在”，可以直接认为该手机号/邮箱/用户名未被注册。
- 如果布隆过滤器判断“可能存在”，再查 MySQL 做精确判断。
- MySQL 唯一索引仍然兜底，防止并发注册冲突。

风险和注意点：

- 单个 `bf:user:*` 约 4 MB，三个 key 约十几 MB；开发环境可以接受。
- 生产环境中，大 key 会增加迁移、备份、删除、网络传输、Redis 主从同步成本。
- 这个 key 是 bitmap，不适合直接在 Tiny RDM 中查看内容。
- 删除时建议用 `UNLINK bf:user:phone`，不要在生产高峰期用阻塞式 `DEL`。

如果要减小它：

```yaml
hm:
  bloom:
    bit-size: 4194304
    hash-count: 5
```

这会把单个布隆过滤器降到约 512 KB，但误判率会变高。也可以后续切换 RedisBloom 模块或按业务只保留 `phone` 布隆过滤器。

## 4. MinIO 存储内容

MinIO bucket：

```text
hm-badminton
```

控制台：

```text
http://localhost:9001
账号：minioadmin
密码：minioadmin123
```

当前 MinIO 为空是正常的，原因是：

1. `docker-compose.yml` 只负责创建 bucket。
2. 后端 `FileStorageService` 也只会确保 bucket 存在。
3. `data.sql` 中的图片目前使用的是外部 URL，例如 Unsplash 或高德图片，并没有上传到 MinIO。
4. 只有调用 `/api/files/upload` 上传文件后，MinIO 中才会出现对象。

文件上传后的存储规则：

| 位置 | 存放内容 |
| --- | --- |
| MinIO | 文件二进制本体 |
| MySQL `file_metadata` | 文件 owner、业务类型、业务 id、bucket、objectName、原文件名、contentType、大小、etag、publicUrl |

对象路径规则：

```text
{bizType}/{yyyy}/{MM}/{dd}/{uuid}.{suffix}
```

示例：

```text
venue/2026/06/29/2fd48d79-b25e-4a56-bc7a-0d8f3d9b2d91.jpg
avatar/2026/06/29/7c0b5d58-9f9a-4a80-9e2f-665b0e0107f3.png
```

测试上传：

```bash
curl -F "file=@/path/to/test.jpg" \
  -F "bizType=venue" \
  -F "bizId=1" \
  http://localhost:8088/api/files/upload
```

上传成功后：

- MinIO 控制台能看到对象文件。
- MySQL `file_metadata` 表能看到元数据。

查询元数据：

```sql
select id, biz_type, biz_id, bucket_name, object_name, original_filename, file_size, public_url
from file_metadata
order by id desc
limit 10;
```

## 5. 如何查看当前数据

### MySQL

```bash
docker exec -it hm-badminton-mysql mysql -uroot -p123456 hm_badminton
```

常用 SQL：

```sql
show tables;
select id, phone, nickname from users limit 10;
select * from venue_orders order by id desc limit 10;
select * from file_metadata order by id desc limit 10;
```

场所订单接口统一使用：

```text
GET /venue/orders
POST /venue/orders
POST /venue/orders/{id}/pay
```

### Redis

```bash
docker exec -it hm-badminton-redis redis-cli
```

常用命令：

```redis
scan 0
type bf:user:phone
strlen bf:user:phone
memory usage bf:user:phone
ttl login:code:13800000001
hgetall login:token:{token}
smembers seckill:users:1
```

### RocketMQ

```bash
docker exec -it hm-badminton-rocketmq-broker sh mqadmin topicList \
  -n rocketmq-namesrv:9876
```

当前主题：

```text
hm-seckill-order
```

秒杀请求链路为：前端提交 -> Redis Lua 预扣库存和记录用户 -> Redisson 分布式锁保护同一用户并发 -> RocketMQ 投递订单消息 -> 消费者异步写入 `seckill_orders` 并扣减数据库库存。

### MinIO

浏览器打开：

```text
http://localhost:9001
```

如果 bucket 存在但没有对象，说明还没有业务上传文件。

## 6. 后续维护规则

后续做以下修改时，需要同步更新本文档：

- 新增或删除 MySQL 表。
- 新增 Redis key、调整 key 命名、调整 TTL、调整数据类型。
- 修改布隆过滤器大小、hash 次数、实现方式。
- 修改 MinIO bucket、对象路径规则、文件业务类型。
- 将外链图片迁移到 MinIO。
- 改动登录态、验证码、秒杀、缓存、文件上传相关逻辑。
