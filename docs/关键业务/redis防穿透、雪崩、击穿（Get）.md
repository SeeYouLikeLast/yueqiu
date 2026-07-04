# Redis 防穿透、雪崩、击穿

本文记录当前项目 Redis 缓存保护策略，以及对应代码位置。后续如果 Redis 缓存 key、TTL 或缓存策略调整，需要同步更新本文档。

## 1. 三个问题分别是什么

| 问题   | 典型场景                            | 后果                   | 当前项目策略                 |
| ---- | ------------------------------- | -------------------- | ---------------------- |
| 缓存穿透 | 请求不存在的 id，例如 `equipment:999999` | Redis 查不到，每次都打 MySQL | 空值缓存 `__NULL__`、限制接收参数 |
| 缓存雪崩 | 大量 key 同时过期                     | 同一时刻大量请求打 MySQL      | TTL 加随机抖动              |
| 缓存击穿 | 热点 key 过期，例如秒杀活动详情              | 瞬间大量并发打 MySQL        | 逻辑过期 + 互斥重建            |

## 2. 当前代码位置

核心工具类：

```text
backend/src/main/java/com/hm/badminton/utils/CacheClient.java
```

Redis 常量：

```text
backend/src/main/java/com/hm/badminton/constants/RedisConstants.java
```

TTL 抖动工具：

```text
backend/src/main/java/com/hm/badminton/utils/RedisTtl.java
```

## 3. 防缓存穿透：空值缓存、接受参数时会限制参数

**当前接受参数时会限制参数（1/2）**

当前项目使用 `CacheClient.queryWithPassThrough(...)` 处理普通详情缓存。

核心逻辑：

```text
1. 先查 Redis
2. Redis 命中正常数据，直接返回
3. Redis 命中 `__NULL__`，直接抛 404，不查 MySQL
4. Redis 未命中，查 MySQL
5. MySQL 查不到，写入 `__NULL__`，TTL 2 分钟
6. MySQL 查到，写入正常缓存
```

空值常量：

```java
public static final String CACHE_NULL_VALUE = "__NULL__";
public static final Duration CACHE_NULL_TTL = Duration.ofMinutes(2);
```

当前使用空值缓存的 key：

| Key | 业务 | 策略 |
| --- | --- | --- |
| `equipment:{id}` | 装备详情 | 普通缓存 + 空值缓存 |
| `venue:item:{id}` | 场所售卖项目详情 | 普通缓存 + 空值缓存 |
| `user:profile:{id}` | 用户公开主页基础信息 | 普通缓存 + 空值缓存 |
| `blog:{id}` | 博客基础详情 | 普通缓存 + 空值缓存 |
| `seckill:activity:{type}:{activityId}` | 秒杀活动详情 | 逻辑过期 + 空值缓存 |

注意：空值缓存只能缓解“请求不存在 id”的穿透。如果攻击者每次都构造不同随机 id，仍需要限流、鉴权、参数校验、布隆过滤器等手段配合。

逆地理编码也做了短时间缓存，但它不是数据库穿透场景，而是为了减少重复调用高德：

| Key | 业务 | 策略 |
| --- | --- | --- |
| `amap:regeo:{lng},{lat}` | 高德逆地理编码结果，经纬度保留 4 位小数 | 普通缓存，TTL 30 分钟 |

## 4. 防缓存雪崩：TTL 随机抖动

当前项目使用 `RedisTtl.withJitter(...)` 给 TTL 加随机值。

代码位置：

```text
backend/src/main/java/com/hm/badminton/utils/RedisTtl.java
```

核心逻辑：

```java
public static Duration withJitter(Duration base, long maxJitterSeconds) {
    if (base == null || maxJitterSeconds <= 0) {
        return base;
    }
    long jitter = ThreadLocalRandom.current().nextLong(maxJitterSeconds + 1);
    return base.plusSeconds(jitter);
}
```

当前使用 TTL 抖动的地方：

| Key                                 | 基础 TTL     | 抖动         |
| ----------------------------------- | ---------- | ---------- |
| `login:token:{token}`               | 120 分钟     | 默认最多 10 分钟 |
| `equipment:{id}`                    | 30 分钟      | 最多 5 分钟    |
| `venue:item:{id}`                   | 30 分钟      | 最多 5 分钟    |
| `user:profile:{id}`                 | 30 分钟      | 最多 5 分钟    |
| `blog:{id}`                         | 30 分钟      | 最多 5 分钟    |
| `amap:regeo:{lng},{lat}`            | 30 分钟      | 无          |
| `seckill:stock:{type}:{activityId}` | 活动结束后 1 小时 | 最多 30 分钟   |
| `seckill:users:{type}:{activityId}` | 活动结束后 1 小时 | 最多 30 分钟   |

作用：

```text
避免大量 key 在同一秒过期，降低数据库瞬时压力。
```

## 5. 防缓存击穿：逻辑过期

当前项目只有一个热点 key 使用逻辑过期：

```text
seckill:activity:{type}:{activityId}
```

对应代码：

```text
backend/src/main/java/com/hm/badminton/service/trade/impl/SeckillService.java
```

调用：

```java
cacheClient.queryWithLogicalExpire(
        activityMetaKey(type, activityId),
        SeckillActivity.class,
        () -> type == TradeType.VENUE
                ? seckillMapper.selectVenueActivity(activityId)
                : seckillMapper.selectEquipmentActivity(activityId),
        RedisConstants.CACHE_LOGICAL_TTL,
        "秒杀活动不存在");
```

缓存值结构：

```json
{
  "data": {
    "id": 1,
    "type": 2,
    "productId": 1,
    "seckillPrice": 399.00
  },
  "expireTime": "2026-07-04T10:30:00"
}
```

逻辑过期流程：

```text
1. 查询 Redis
2. 缓存不存在，查 MySQL 并写入逻辑过期缓存
3. 缓存存在且未逻辑过期，直接返回
4. 缓存存在但已逻辑过期，先返回旧数据
5. 尝试获取重建锁 `lock:cache:rebuild:{key}`
6. 获取锁成功后，后台线程异步查 MySQL 并重建缓存
7. 获取锁失败的请求继续返回旧数据
```

重建锁：

```java
public static final String CACHE_REBUILD_LOCK_KEY = "lock:cache:rebuild:";
public static final Duration CACHE_REBUILD_LOCK_TTL = Duration.ofSeconds(10);
```

逻辑过期时间：

```java
public static final Duration CACHE_LOGICAL_TTL = Duration.ofMinutes(30);
```

为什么只给秒杀活动详情使用逻辑过期：

```text
秒杀活动详情是天然热点 key。秒杀开始前后，大量用户会同时访问同一个活动。
逻辑过期可以避免活动详情缓存过期瞬间，大量请求同时打到 MySQL。
```

## 6. 哪些 key 不使用逻辑过期

以下 key 不使用逻辑过期：

| Key                                 | 原因                            |
| ----------------------------------- | ----------------------------- |
| `equipment:{id}`                    | 当前作为普通详情缓存，使用 TTL 抖动 + 空值缓存即可 |
| `venue:item:{id}`                   | 当前作为普通详情缓存，使用 TTL 抖动 + 空值缓存即可 |
| `user:profile:{id}`                 | 用户信息不是秒杀级热点，且关注/是否本人要实时计算     |
| `blog:{id}`                         | 点赞/关注态要实时计算，暂不作为热点 key        |
| `login:token:{token}`               | 登录态必须严格过期                     |
| `login:code:{phone}`                | 验证码必须严格过期                     |
| `seckill:stock:{type}:{activityId}` | 秒杀库存必须实时扣减，不能返回旧值             |
| `seckill:users:{type}:{activityId}` | 一人一单判断必须实时，不能返回旧值             |
| `blog:liked:{blogId}`               | 点赞集合是交互状态，不适合逻辑过期             |
| `feed:{userId}`                     | 用户个性化 Feed，不适合逻辑过期            |

## 7. 当前 key 策略汇总

| Key | 防穿透 | 防雪崩 | 防击穿 |
| --- | --- | --- | --- |
| `seckill:activity:{type}:{activityId}` | 空值缓存 | 逻辑过期，不依赖物理 TTL | 逻辑过期 + 重建锁 |
| `equipment:{id}` | 空值缓存 | TTL 随机抖动 | 暂无逻辑过期 |
| `venue:item:{id}` | 空值缓存 | TTL 随机抖动 | 暂无逻辑过期 |
| `user:profile:{id}` | 空值缓存 | TTL 随机抖动 | 暂无逻辑过期 |
| `blog:{id}` | 空值缓存 | TTL 随机抖动 | 暂无逻辑过期 |
| `login:token:{token}` | 不适用 | TTL 随机抖动 | 不适用 |
| `seckill:stock:{type}:{activityId}` | 不适用 | TTL 随机抖动 | 不适用，强一致扣减 |
| `seckill:users:{type}:{activityId}` | 不适用 | TTL 随机抖动 | 不适用，强一致判断 |

## 8. 注意事项

### 8.1 逻辑过期会短暂返回旧数据

逻辑过期的特点是：

```text
过期后先返回旧值，再异步重建。
```

它适合热点读场景，不适合强一致数据。

因此不要给下面这些 key 使用逻辑过期：

```text
秒杀库存
已抢用户集合
验证码
登录 token
支付状态
购物车状态
```

### 8.2 删除缓存时要注意数据一致性

当前写操作会删除相关详情缓存：

| 写操作 | 删除 key |
| --- | --- |
| 装备普通购买扣库存 | `equipment:{id}` |
| 装备秒杀落库后 | `equipment:{id}` |
| 场所下单扣库存 | `venue:item:{id}` |
| 博客点赞/取消点赞 | `blog:{id}` |

### 8.3 空值缓存 TTL 不宜过长

空值缓存过长会导致：

```text
数据库刚插入新数据，但 Redis 里仍然是 `__NULL__`
```

所以当前空值 TTL 设置为 2 分钟。

## 9. 后续可优化

1. 给高频列表接口增加短 TTL 缓存，但需要考虑分页、筛选条件和更新一致性。
2. 对异常热点 id 增加接口限流。
3. 如果 Redis 改为 Cluster，秒杀 Lua 涉及的 key 应使用 hash tag，例如 `seckill:{2:1001}:stock`。
4. 给逻辑过期重建线程池增加优雅关闭和监控指标。
5. 对缓存重建失败增加日志和告警。
