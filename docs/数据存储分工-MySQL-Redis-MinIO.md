# 数据存储分工：MySQL / Redis / MinIO / RocketMQ

本文档是“约个球”项目的数据存储设计维护入口。新增数据库表、Redis Key、MinIO 对象规则或 RocketMQ Topic 时，必须同步更新本文档和 `RedisConstants.java`。

## 1. 总体边界

| 组件       | 保存内容                                   | 适合原因            | 是否为最终事实来源     |
| -------- | -------------------------------------- | --------------- | ------------- |
| MySQL    | 用户、场所扩展、商品、库存、订单、社区、评价、约球、AI 会话、文件元数据  | 可事务、可关联、可索引、可审计 | 是             |
| Redis    | 登录态、验证码、缓存、布隆过滤器、秒杀状态、Feed、限流、AI 匿名历史/结构化记忆/RAG 向量 | 高并发、低延迟、支持 TTL | 通常否；游客历史在 TTL 内是临时事实源 |
| MinIO    | 头像、博客图、评价图、演示图及用户上传文件本体                | 适合二进制大对象        | 文件本体是；元数据否    |
| RocketMQ | 秒杀订单消息                                 | 削峰、异步落库、失败重试    | 否，订单最终落 MySQL |

原则：

- MySQL 保存长期业务最终状态；大多数 Redis 数据可重建。游客 AI 历史明确是 7 天临时数据，Redis/Cookie 丢失后不承诺恢复。
- MinIO 只保存对象本体，归属用户、业务类型、对象路径和 MIME 类型由 MySQL `file_metadata` 管理。
- 秒杀先在 Redis 原子预扣，再通过 RocketMQ 或同步补偿写入 MySQL；数据库唯一约束负责最终兜底。
- 登录用户 AI 会话和消息保存到 MySQL；游客会话、消息和结构化需求只保存到 Redis，并支持历史查看、切换与删除。
- RAG 原文仍在 MySQL；Redis 只缓存按内容哈希生成的 Embedding 向量，失效后可重新计算。

## 2. 运行位置与端口

### 本地开发（Windows + WSL Docker）

| 服务 | 容器 | 宿主机端口 |
| --- | --- | --- |
| MySQL | `hm-badminton-mysql` | `3307 -> 3306` |
| Redis | `hm-badminton-redis` | `6379` |
| MinIO API / Console | `hm-badminton-minio` | `9000 / 9001` |
| RocketMQ NameServer / Broker | 可选 MQ Profile | `9876 / 10909 / 10911 / 10912` |

### 生产环境（ECS）

```text
Internet -> Nginx :80/:443 -> Vue 静态文件、/api
                                  -> Spring Boot :8088（仅 127.0.0.1）
                                  -> Docker: MySQL / Redis / MinIO
                                  -> RocketMQ（可选）
```

- 仅 Nginx 的 `80/443` 面向公网。
- MySQL、Redis、MinIO、RocketMQ 和 Spring Boot 均绑定 `127.0.0.1`，不开放安全组端口。
- MinIO 公开图片由 Nginx `/objects/` 直连代理，列表图片使用懒加载；Java 不参与公开图片字节转发。
- 生产环境配置位于 `/etc/hm-badminton/app.env`，禁止把密码、JWT Secret、邮件授权码、AI Key 提交到仓库。

## 3. MySQL：最终业务数据

数据库名：`hm_badminton`。连接信息从环境变量 `MYSQL_URL`、`MYSQL_USERNAME`、`MYSQL_PASSWORD` 读取。

### 核心表

| 分类 | 表 | 说明 |
| --- | --- | --- |
| 账号与资料 | `users`、`player_profiles` | 账号、邮箱/手机号、昵称、等级、城市、定位和球友资料 |
| 运动目录 | `sports`、`equipment_categories` | 球类和装备分类 |
| 场所与售卖 | `place`、`venue`、`venue_inventory` | `place` 为真实高德场所/本地扩展；`venue` 为挂载在场所顺序上的团购、单场、私教商品；`venue_inventory` 为具体日期时段库存 |
| 装备 | `equipment`、`cart_equipment` | 装备商品和用户购物车 |
| 普通订单 | `order_venue`、`order_equipment`、`order_equipment_item` | 场所商品订单、装备订单及明细 |
| 秒杀 | `seckill_venue`、`seckill_equipment`、`order_seckill_venue`、`order_seckill_equipment` | 两类秒杀活动和对应订单 |
| 社区 | `blogs`、`blog_archive`、`follows` | 博客、归档博客、关注关系 |
| 场所评价 | `venue_reviews`、`venue_favorites` | 平台自有评价和收藏；不抓取第三方平台评价 |
| 约球 | `sport_activities`、`sport_activity_members` | 活动、发起人和参与成员 |
| AI | `agent_conversation`、`agent_message` | 仅登录用户的会话标题、用户问题、AI 回答、结构化需求及卡片快照 |
| 文件 | `file_metadata` | MinIO 对象路径、归属人、类型、大小、MIME、访问 URL 等元数据 |

命名约定：

- `place` 是真实场所，搜索结果主要来自高德；`venue` 是可售场馆商品，二者不能混用。
- 场馆商品使用 `city + sport_code + place_rank` 挂载到同一类高德结果，不保存不必要的重复场馆副本。
- 普通订单和秒杀订单按“场所 / 装备”分表，便于索引、库存和订单展示各自独立演进。

### 初始化策略

| 环境        | `spring.sql.init.mode` | 行为                                         |
| --------- | ---------------------- | ------------------------------------------ |
| 本地开发      | `always`               | 启动时执行 `schema.sql` 与 `data.sql`，用于重建轻量演示数据 |
| 生产 `prod` | `never`                | 重启服务不会重建表、清空订单或覆盖 MinIO 元数据                |

生产数据初始化只能在新环境人工执行 `deploy/production/initialize-demo-data.sh`；日常部署和重启不得再次执行初始化脚本。

## 4. Redis：缓存、临时态与高并发状态

Redis Key 常量统一定义在：`backend/src/main/java/com/hm/badminton/constants/RedisConstants.java`。

### 登录、注册与限流

| Key 模式 | 类型 | 内容 | TTL |
| --- | --- | --- | --- |
| `login:token:{token}` | Hash | 登录用户摘要、城市、等级、经纬度与简要地址 | 120 分钟 + 最多 10 分钟随机抖动；访问时刷新 |
| `login:code:email:{email}` | String | 邮箱登录验证码 | 5 分钟 |
| `login:code:email:cooldown:{email}` | String | 发送冷却标记 | 60 秒 |
| `login:code:email:limit:{email}` | String/Counter | 同邮箱发送次数窗口 | 10 分钟内最多 5 次 |
| `bf:user:phone` / `bf:user:email` / `bf:user:username` | Bitmap（Redis String） | 注册唯一性预判断布隆过滤器 | 永久，可由 MySQL 回填 |
| `agent:rate:user:{id}` / `agent:rate:ip:{ip}` | String/Counter | AI 用户/IP 限流计数 | 1 分钟 |

布隆过滤器显示为 Big Key 属于正常现象：Bitmap 底层使用 Redis String。它只负责快速判断“可能存在”，注册时仍必须依赖 MySQL 唯一索引做精确确认。

### 详情与目录缓存

| Key 模式 | 内容 | 策略 |
| --- | --- | --- |
| `seckill:activity:{type}:{id}` | 秒杀活动详情 | 逻辑过期 30 分钟；过期时返回旧值并由持锁线程异步重建，防缓存击穿 |
| `equipment:{id}` | 装备详情 | Cache Aside，30 分钟 + 0-5 分钟抖动 |
| `venue:item:{id}` | 场所商品详情 | Cache Aside，30 分钟 + 0-5 分钟抖动 |
| `user:profile:{id}` | 公开用户资料 | Cache Aside，30 分钟 + 0-5 分钟抖动 |
| `blog:{id}` | 博客基础详情 | Cache Aside，30 分钟 + 0-5 分钟抖动 |
| `catalog:sports` | 球类目录 | 24 小时 |
| `social:players:*` / `social:activities:*` | 球友与约球分页列表 | 1 分钟短缓存 |

所有普通详情缓存共享空值缓存：查不到时写入 `__NULL__`，TTL 为 2 分钟，防止恶意 id 反复穿透 MySQL。

### 高德位置缓存

| Key 模式                   | 内容                    | TTL             |
| ------------------------ | --------------------- | --------------- |
| `amap:nearby:*`          | 高德附近场所结果；经纬度归并至 4 位小数 | 2 分钟 + 0-30 秒抖动 |
| `amap:regeo:{lng},{lat}` | 逆地理编码结果               | 30 分钟           |
| `amap:ip:{ip}`           | IP 城市级定位结果            | 10 分钟           |

附近搜索必须带 `lng`、`lat` 才能返回真实附近结果；城市作为 `X-Location-City` 请求头用于区域约束，不再反复写入 URL 查询串。

### 社区、秒杀与 AI

| Key 模式 | 类型 | 内容 | TTL / 重建方式 |
| --- | --- | --- | --- |
| `follows:{userId}` | Set | 用户关注的博主 | 可由 MySQL 重建 |
| `blog:liked:{blogId}` | ZSet | 点赞用户，score 为点赞时间 | 可由 MySQL 点赞数重建 |
| `feed:{userId}` | ZSet | 普通博主推送的 Feed 收件箱 | 可由博客与关注关系重建 |
| `blog:outbox:{bigVId}` | ZSet | 大 V 拉模式博客发件箱 | 可由博客重建 |
| `seckill:stock:{type}:{id}` | String | 秒杀实时库存 | 至活动结束后 1 小时 + 抖动 |
| `seckill:users:{type}:{id}` | Set | 已抢购用户 id | 至活动结束后 1 小时 + 抖动 |
| `lock:seckill:order:{type}:{id}:{userId}` | Redisson Lock | 分布式一人一单互斥锁 | 短生命周期，Redisson 自动管理 |
| `lock:cache:rebuild:{key}` | String | 逻辑过期缓存重建锁 | 10 秒 |
| `agent:conversation:requirement:{conversationId}` | String | 城市、运动、时段、预算、距离、水平、偏好等结构化需求 | 登录默认 60 分钟；游客与匿名历史 TTL 对齐，均带抖动 |
| `agent:anonymous:conversations:{anonymousId}` | ZSet | 游客会话索引，score 为更新时间 | 7 天，访问时续期；最多 30 个会话 |
| `agent:anonymous:conversation:{anonymousId}:{conversationId}` | String/JSON | 游客会话标题、结构化需求和时间 | 7 天，访问时续期 |
| `agent:anonymous:messages:{anonymousId}:{conversationId}` | List/JSON | 游客问题、AI 回答和业务卡片快照 | 7 天，访问时续期；最多 100 条 |
| `agent:rag:embedding:{sha256}` | String/JSON | 博客、评价、装备心得的可重建向量 | 默认 7 天 |

游客浏览器由后端签发 `HttpOnly`、`SameSite=Lax` 的 `hm_agent_guest` Cookie。匿名 ID 同时进入 Redis key，接口会校验 Cookie 与会话归属；会话使用负数 ID，避免与 MySQL 正数 ID 混淆。清除 Cookie、更换浏览器或 Redis TTL 到期后，游客历史不能恢复，也不会自动并入登录账号。

AI 的轻量 RAG 不保存价格、库存、可售时段和活动人数。场馆评价、博客和装备心得从 MySQL 按城市、球类和实体 ID 约束后检索；Embedding 不可用时使用词法检索，业务硬事实始终来自实时工具。

秒杀库存和用户集合由 Redis Lua 脚本原子扣减与记录，随后才发送 MQ 消息/进行同步落库。订单消费者和数据库唯一约束共同保证重复消息不会创建重复订单。

## 5. MinIO：文件本体与公开图片

Bucket：`hm-badminton`。

| 内容 | 保存位置 | 访问方式 |
| --- | --- | --- |
| 演示头像、场所封面、商品图、博客图、评价图 | `demo/...` 对象路径 | `https://站点域名/objects/hm-badminton/demo/...` |
| 用户头像、博客图片、评价图片、附件 | 按业务类型和日期生成对象路径 | 通过文件元数据和业务权限控制 |

对象路径建议：

```text
{bizType}/{yyyy}/{MM}/{dd}/{uuid}.{suffix}
```

上传流程：

```text
登录用户 -> /api/files/upload -> 校验归属、文件大小、MIME 与后缀
        -> MinIO 上传对象 -> MySQL 写入 file_metadata -> 返回公开/业务 URL
```

删除流程必须按当前用户校验 `file_metadata.owner_user_id`。MySQL 事务不能回滚 MinIO 对象操作：上传后元数据写入失败时应尝试删除对象，长期残留对象由运维任务巡检清理。

生产环境中 MinIO 的 `9000/9001` 不开放公网；公开读取只走 Nginx `/objects/`，控制台只通过 SSH 隧道访问。

## 6. RocketMQ：秒杀异步落库

Topic：`hm-seckill-order`。

```text
请求抢购
  -> Redis Lua：库存大于 0 且用户未购买时原子预扣
  -> Redisson：同用户同活动分布式互斥
  -> RocketMQ 投递订单消息
  -> Consumer 异步创建 MySQL 秒杀订单
  -> 成功后失效相关商品详情缓存
```

ECS 轻量模式使用 `prod,lite`，默认不启动 RocketMQ；MQ 未部署或发送失败时，业务以相同订单号同步补偿落库。启用 `prod,mq-lite` 后才运行受限内存的单节点 RocketMQ，适合演示，不适合作为高并发生产集群。

## 7. 查看与排障

所有命令在对应 Docker 环境执行。密码从 `.env` 或 `/etc/hm-badminton/app.env` 读取，不要写入 Shell 历史或文档。

```bash
# 容器状态和内存
cd /opt/hm-badminton/deploy/production
docker compose --env-file .env ps
docker stats --no-stream

# MySQL 交互式连接：运行后手动输入密码
docker exec -it hm-badminton-mysql mysql -uroot -p hm_badminton

# Redis
docker exec -it hm-badminton-redis redis-cli
```

常用 Redis 命令：

```redis
SCAN 0 MATCH amap:nearby:* COUNT 20
TTL login:token:{token}
HGETALL login:token:{token}
GET seckill:stock:1:{id}
SMEMBERS seckill:users:2:{id}
MEMORY USAGE bf:user:email
```

常用 SQL：

```sql
SHOW TABLES;
SELECT id, email, nickname, city FROM users LIMIT 10;
SELECT id, title, user_id, status, created_at FROM blogs ORDER BY id DESC LIMIT 10;
SELECT id, product_name, status, created_at FROM order_venue ORDER BY id DESC LIMIT 10;
SELECT id, total_amount, status, created_at FROM order_equipment ORDER BY id DESC LIMIT 10;
SELECT id, owner_user_id, biz_type, object_name, size FROM file_metadata ORDER BY id DESC LIMIT 10;
SELECT id, user_id, title, updated_at FROM agent_conversation ORDER BY updated_at DESC LIMIT 10;
```

## 8. 维护清单

修改下列内容时同步维护本文档：

1. 新增 MySQL 表、关键索引或订单状态机。
2. 新增/删除 Redis Key、TTL、缓存重建或限流策略。
3. 修改 MinIO Bucket、对象路径、公开访问策略、上传限制或清理任务。
4. 新增 RocketMQ Topic、Consumer Group、消息幂等规则或降级策略。
5. 修改本地/生产端口、容器内存限制、初始化方式或备份恢复流程。
- 生产环境的数据备份、恢复顺序和监控项见 [备份、恢复与监控](备份恢复与监控.md)。
- MinIO 当前 Bucket 为匿名下载，公开图片可以直连；私密文件必须改用独立私有 Bucket 和鉴权下载。
- Redis 丢失后不应丢订单事实，但会使登录态、缓存、Feed 和游客 AI 历史失效；恢复时先保证 MySQL 和 MinIO 正确。
- `schema.sql`、`data.sql` 只用于开发或首次演示初始化，生产更新必须使用增量迁移。
