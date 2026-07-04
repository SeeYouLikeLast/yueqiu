# 秒杀业务逻辑与并发控制

本文记录当前项目秒杀模块的业务流程、并发控制原理、关键代码块和已知不足。后续如果秒杀链路调整，需要同步维护本文档。

## 1. 业务目标

当前秒杀支持两类商品：

| 类型 | type | 活动表 | 订单表 |
| --- | --- | --- | --- |
| 场所商品秒杀 | `1` | `seckill_venue` | `order_seckill_venue` |
| 装备商品秒杀 | `2` | `seckill_equipment` | `order_seckill_equipment` |

秒杀要解决三个核心问题：

1. 高并发请求不能直接打到 MySQL。
2. 库存不能超卖。
3. 同一个用户对同一个秒杀活动只能成功一次。

当前采用的技术组合：

```text
Redis Lua 原子预扣库存
+ Redisson 分布式锁
+ RocketMQ 异步削峰落库
+ MySQL 条件扣库存
+ MySQL 唯一索引兜底一人一单
```

## 2. 接口入口

前端秒杀下单接口：

```http
POST /api/seckill/{type}/{seckillId}
```

示例：

```http
POST /api/seckill/2/1001
```

含义：

```text
用户抢购 type=2 的装备秒杀活动 1001
```

Controller 位置：

```text
backend/src/main/java/com/hm/badminton/controller/trade/SeckillController.java
```

核心逻辑：

```java
@PostMapping("/{type:[12]}/{seckillId}")
public ApiResponse<Map<String, Long>> typedSubmit(@PathVariable Integer type, @PathVariable Long seckillId) {
    return ApiResponse.ok(Map.of("orderId", seckillService.submit(userContext.requireUserId(), type, seckillId)));
}
```

Controller 只做三件事：

1. 从路径中读取秒杀类型和秒杀活动 id。
2. 从 `UserContext` 中获取当前登录用户 id。
3. 调用 `ISeckillService.submit()`。

真正的并发控制在 Service 层完成。

## 3. 完整流程

整体链路如下：

```text
用户点击秒杀
  ↓
SeckillController
  ↓
SeckillService.submit()
  ↓
校验活动是否存在、是否在秒杀时间内
  ↓
Redisson 获取 用户 + 活动 维度的分布式锁
  ↓
Redis Lua 原子判断库存、一人一单，并预扣 Redis 库存
  ↓
生成订单号
  ↓
发送 RocketMQ 秒杀订单消息
  ↓
接口立即返回 orderId
  ↓
SeckillOrderConsumer 消费 MQ 消息
  ↓
SeckillOrderMessageService.createOrder()
  ↓
MySQL 条件扣减秒杀活动库存
  ↓
MySQL 插入秒杀订单
```

前端拿到 `orderId` 时，只代表秒杀请求已进入异步下单流程，不代表 MySQL 订单一定已经写入完成。

## 4. Redis Key 设计--雪崩

相关常量位置：

```text
backend/src/main/java/com/hm/badminton/constants/RedisConstants.java
```

当前 key：

| Key                                               | 类型     | 作用           |
| ------------------------------------------------- | ------ | ------------ |
| `seckill:stock:{type}:{activityId}`               | String | Redis 中的秒杀库存 |
| `seckill:users:{type}:{activityId}`               | Set    | 已成功预扣的用户集合   |
| `lock:seckill:order:{type}:{activityId}:{userId}` | Lock   | 用户级分布式锁      |
| `seckill:activity:{type}:{activityId}`            | String(JSON) | 秒杀活动元数据缓存，保存 `data + expireTime` |

例如：

```text
seckill:stock:2:1001
seckill:users:2:1001
lock:seckill:order:2:1001:5
seckill:activity:2:1001
```

TTL 设计：

```text
活动结束时间 + 1 小时 + 随机抖动
```

随机抖动用于降低大量秒杀 key 同时过期造成的缓存雪崩风险。

其中 `seckill:activity:{type}:{activityId}` 已改为逻辑过期热点缓存：Redis key 本身不依赖物理 TTL，value 中保存逻辑过期时间。逻辑过期后请求仍先返回旧活动数据，并由拿到重建锁的线程异步刷新缓存，避免秒杀活动热点 key 过期时大量请求同时访问 MySQL。

## 5. Redisson 分布式锁

关键代码位置：

```text
backend/src/main/java/com/hm/badminton/service/trade/impl/SeckillService.java
```

关键代码：

```java
RLock lock = redissonClient.getLock(
        RedisConstants.SECKILL_ORDER_LOCK_KEY + tradeType + ":" + activityId + ":" + userId);
if (!tryUserLock(lock)) {
    throw new BusinessException(409, "不能重复抢购");
}
```

锁的粒度是：

```text
商品类型 + 秒杀活动 id + 用户 id
```

也就是同一个用户抢同一个活动时才互斥。

它的作用：

1. 防止同一个用户短时间内重复点击，产生多个并发请求。
2. 防止同一个用户的多个请求同时执行 Lua 和发送 MQ。
3. 降低 Redis、RocketMQ 和 MySQL 的重复压力。

它不负责锁整个商品库存。

如果锁整个商品，会导致所有用户串行抢购，吞吐量很低。当前只锁用户维度，其他用户可以并发抢同一个活动，库存安全交给 Redis Lua 和 MySQL 条件扣减。

在分布式部署下，只要多个后端实例连接同一个 Redis，Redisson 锁就可以跨 JVM、跨机器生效。

## 6. Redis Lua 原子预扣

Lua 文件位置：

```text
backend/src/main/resources/lua/seckill.lua
```

脚本内容：

```lua
local stock = tonumber(redis.call('get', KEYS[1]) or '0')
if stock <= 0 then
  return 1
end
if redis.call('sismember', KEYS[2], ARGV[1]) == 1 then
  return 2
end
redis.call('decr', KEYS[1])
redis.call('sadd', KEYS[2], ARGV[1])
return 0
```

入参：

```text
KEYS[1] = seckill:stock:{type}:{activityId}
KEYS[2] = seckill:users:{type}:{activityId}
ARGV[1] = userId
```

返回值：

| 返回值 | 含义         |
| --- | ---------- |
| `0` | 预扣成功       |
| `1` | Redis 库存不足 |
| `2` | 用户已经抢过     |

为什么使用 Lua：

普通 Redis 命令如果分开执行，会有并发问题。例如：

```text
GET stock
SISMEMBER user
DECR stock
SADD user
```

这些命令之间可能被其他请求插入，导致并发安全问题。

Lua 脚本在 Redis 内部作为一个整体执行，具有原子性。这样可以保证：

1. 判断库存和扣库存是原子的。
2. 判断用户是否抢过和记录用户是原子的。
3. Redis 层不会出现库存扣成负数。

Java 调用位置：

```java
Long code = redisTemplate.execute(seckillScript,
        List.of(stockKey(type, activityId), userKey(type, activityId)),
        String.valueOf(userId));
```

## 7. RocketMQ 异步落库

当前项目没有在秒杀请求线程里直接写 MySQL 订单，而是先发送 RocketMQ 消息。

发送位置：

```text
backend/src/main/java/com/hm/badminton/service/trade/impl/SeckillService.java
```

核心代码：

```java
long orderId = idGenerator.nextId();
String payload = serializeSeckillMessage(new SeckillOrderMessage(
        orderId,
        tradeType,
        activity.getId(),
        activity.getProductId(),
        userId,
        activity.getSeckillPrice()));

sendSeckillOrderMessage(payload);
return orderId;
```

RocketMQ 的作用：

1. 秒杀请求快速返回，不同步等待 MySQL 写入。
2. 高峰流量进入 MQ，由消费者慢慢处理。
3. 削减数据库瞬时压力。
4. 秒杀服务和订单落库逻辑解耦。

Topic 常量：

```text
backend/src/main/java/com/hm/badminton/constants/MqConstants.java
```

```java
public static final String SECKILL_ORDER_TOPIC = "hm-seckill-order";
public static final String SECKILL_ORDER_CONSUMER_GROUP = "hm-seckill-order-consumer";
```

## 8. Consumer 异步创建订单

消费者位置：

```text
backend/src/main/java/com/hm/badminton/mq/SeckillOrderConsumer.java
```

消费者收到消息后调用：

```text
ISeckillOrderMessageService.createOrder()
```

落库 Service 位置：

```text
backend/src/main/java/com/hm/badminton/service/trade/impl/SeckillOrderMessageService.java
```

关键代码：

```java
@Transactional
public void createOrder(SeckillOrderMessage message) {
    int type = TradeType.require(message.getType());
    Integer exists = type == TradeType.VENUE
            ? seckillMapper.countUserVenueOrder(message.getUserId(), message.getActivityId())
            : seckillMapper.countUserEquipmentOrder(message.getUserId(), message.getActivityId());
    if (exists != null && exists > 0) {
        return;
    }
    int updated = type == TradeType.VENUE
            ? seckillMapper.deductVenueActivityStock(message.getActivityId())
            : seckillMapper.deductEquipmentActivityStock(message.getActivityId());
    if (updated == 0) {
        throw new BusinessException("秒杀库存已抢完");
    }
    if (type == TradeType.VENUE) {
        seckillMapper.insertVenueOrder(...);
    } else {
        seckillMapper.insertEquipmentOrder(...);
        seckillMapper.deductEquipmentStockLenient(message.getProductId());
    }
}
```

这段逻辑有一个事务：

```java
@Transactional
```

它保证在 MySQL 层：

```text
扣减秒杀活动库存 + 插入秒杀订单
```

要么一起成功，要么一起回滚。

## 9. MySQL 防超卖

Mapper 位置：

```text
backend/src/main/java/com/hm/badminton/mapper/trade/SeckillMapper.java
```

秒杀活动库存扣减：

```java
@Update("update seckill_equipment set stock = stock - 1 where id = #{activityId} and stock > 0")
int deductEquipmentActivityStock(@Param("activityId") Long activityId);

@Update("update seckill_venue set stock = stock - 1 where id = #{activityId} and stock > 0")
int deductVenueActivityStock(@Param("activityId") Long activityId);
```

关键点是：

```sql
where stock > 0
```

在 MySQL 中，这个 update 是原子的。多个消费者并发扣同一个活动库存时，只有库存大于 0 的请求能更新成功。

如果库存已经为 0：

```text
updated = 0
```

代码会抛出：

```java
throw new BusinessException("秒杀库存已抢完");
```

因此数据库层也能兜底防超卖。

## 10. MySQL 一人一单兜底

表结构位置：

```text
backend/src/main/resources/db/schema.sql
```

装备秒杀订单表：

```sql
create table order_seckill_equipment (
  id bigint primary key,
  seckill_id bigint not null,
  equipment_id bigint not null,
  user_id bigint not null,
  amount decimal(10, 2) not null,
  status varchar(16) not null default '已抢到',
  created_at datetime not null default current_timestamp,
  unique key uk_seckill_equipment_user (user_id, seckill_id),
  key idx_order_seckill_equipment_user (user_id, created_at)
) engine=InnoDB default charset=utf8mb4;
```

场所秒杀订单表：

```sql
create table order_seckill_venue (
  id bigint primary key,
  seckill_id bigint not null,
  venue_id bigint not null,
  user_id bigint not null,
  amount decimal(10, 2) not null,
  status varchar(16) not null default '已抢到',
  created_at datetime not null default current_timestamp,
  unique key uk_seckill_venue_user (user_id, seckill_id),
  key idx_order_seckill_venue_user (user_id, created_at)
) engine=InnoDB default charset=utf8mb4;
```

核心唯一索引：

```sql
unique key uk_seckill_equipment_user (user_id, seckill_id)
unique key uk_seckill_venue_user (user_id, seckill_id)
```

这是真正的最终兜底。

即使 Redis、Redisson 或 MQ 发生异常，只要数据库约束存在，同一个用户对同一个秒杀活动也不能插入两条订单。

## 11. 当前能保证什么

在以下前提下：

```text
Redis 正常
RocketMQ 正常
MySQL schema 已正确执行
多个后端实例连接同一个 Redis 和同一个 MySQL
```

当前链路可以做到：

1. Redis 层库存不会扣成负数。
2. MySQL 秒杀活动库存不会扣成负数。
3. 同一个用户对同一个活动不能成功插入多条订单。
4. 高并发请求不会全部直接打到 MySQL。
5. 多线程、多实例部署下，用户级重复请求可以被 Redisson 锁拦截。

简化理解：

```text
防重复第一层：Redisson 用户级锁
防重复第二层：Redis Set
防重复最终层：MySQL 唯一索引

防超卖第一层：Redis Lua 预扣库存
防超卖最终层：MySQL update stock > 0
```

## 12. 当前不足

### 12.1 MQ 发送超时可能导致 Redis 和 MQ 状态不一致

当前流程是：

```text
Redis 预扣成功
  ↓
syncSend 发送 RocketMQ
  ↓
发送失败则回滚 Redis
```

问题是 `syncSend` 抛异常或超时，不一定代表 MQ 没收到消息。

可能发生这种情况：

```text
RocketMQ 实际收到消息
客户端因为网络抖动认为发送失败
业务代码回滚 Redis 库存和用户集合
Consumer 后续仍然消费消息并创建订单
```

结果：

```text
MySQL 有订单
Redis 库存被加回
Redis 用户集合被移除
```

这会造成 Redis 与 MySQL 不一致。

优化方向：

1. 使用 RocketMQ 事务消息。
2. 使用本地消息表。
3. 使用订单状态表 + 定时补偿任务。
4. 不在发送异常时简单回滚，而是进入待确认状态。

### 12.2 启动时自动预热 Redis 有风险

当前 `SeckillService` 实现了 `ApplicationRunner`，应用启动时会预热秒杀库存和已购用户集合。

风险：

1. 如果秒杀正在进行，某个实例重启，可能覆盖 Redis 中的实时库存。
2. 多实例部署时，每个实例启动都可能执行预热。
3. Redis 中已有预扣但 MQ 尚未落库的数据，可能被启动预热覆盖。

优化方向：

1. 不在应用启动时无条件预热秒杀库存。
2. 改为后台管理端手动预热。
3. 预热时加全局分布式锁。
4. 活动开始前预热，活动中禁止覆盖。

### 12.3 Consumer 对重复消息的处理还可以更严谨

当前 Consumer 落库前会先查：

```java
countUserVenueOrder(...)
countUserEquipmentOrder(...)
```

如果已存在订单，就直接返回。

这对 MQ 重复投递同一条消息是可以的。

但如果出现同一用户、同一活动、不同 orderId 的异常消息，目前只是简单忽略，缺少更细的日志和补偿策略。

优化方向：

1. 区分同一个 `orderId` 重复投递和不同 `orderId` 异常重复。
2. 对异常重复消息记录告警。
3. 保留订单消息处理日志，便于排查。

### 12.4 Redis Cluster 下 Lua Key 可能跨 slot

当前 Lua 使用两个 key：

```text
seckill:stock:2:1001
seckill:users:2:1001
```

如果以后 Redis 换成 Cluster，这两个 key 可能落到不同 slot。Redis Cluster 要求 Lua 脚本涉及的 key 在同一个 slot，否则会报：

```text
CROSSSLOT Keys in request don't hash to the same slot
```

优化方向：

使用 hash tag：

```text
seckill:{2:1001}:stock
seckill:{2:1001}:users
```

大括号中的内容相同，Redis Cluster 会把两个 key 放到同一个 slot。

### 12.5 场所秒杀没有联动真实场地时段库存

当前场所秒杀扣的是：

```text
seckill_venue.stock
```

普通场所购买扣的是类似场地库存、时段库存。

如果业务要求“秒杀场所商品必须真实占用某个场地时段”，还需要联动扣减具体场地时段库存，例如：

```text
venue_inventory
```

否则会出现：

```text
秒杀订单成功
但真实场地时段并没有被锁定
```

这属于业务一致性问题，不是传统意义上的秒杀超卖问题。

### 12.6 装备主表库存扣减是宽松扣减

当前装备秒杀订单落库后会执行：

```java
@Update("update equipment set stock = greatest(stock - 1, 0), sold = sold + 1 where id = #{productId}")
int deductEquipmentStockLenient(@Param("productId") Long productId);
```

这个写法不会让装备主表库存变成负数，但它没有用：

```sql
where stock > 0
```

因此它更像是展示库存和销量的同步，不是严格库存控制。

严格库存控制现在主要在：

```text
seckill_equipment.stock
```

如果要让装备主表库存也严格一致，需要改为条件扣减，并定义清楚普通库存和秒杀库存之间的关系。

### 12.7 前端拿到 orderId 不代表订单已落库

秒杀接口返回：

```json
{
  "orderId": 123
}
```

但订单是 MQ 异步创建的。

所以此时可能存在短暂状态：

```text
前端已经拿到 orderId
MySQL 订单还没创建完成
```

优化方向：

1. 增加订单状态查询接口。
2. 前端展示“排队中 / 下单中 / 已抢到 / 已失败”。
3. 后端提供秒杀结果查询：

```http
GET /api/seckill/result/{orderId}
```

## 13. 推荐优化路线

### 第一阶段：低风险修正

1. 取消应用启动时无条件覆盖 Redis 秒杀库存。
2. 增加后台手动预热接口，并加分布式锁。
3. 增加秒杀结果查询接口。
4. Consumer 对异常重复订单增加日志。
5. Redis key 改成 Cluster 友好的 hash tag。

### 第二阶段：可靠消息

选择一种可靠消息方案：

1. RocketMQ 事务消息。
2. 本地消息表 + 定时补偿。
3. 订单状态表 + 消息重试补偿。

推荐当前项目优先用：

```text
本地消息表 + 定时补偿
```

原因是更适合学习和排查，数据都在 MySQL 中可见，也更容易理解消息最终一致性的全过程。

### 第三阶段：业务库存统一

1. 明确秒杀库存和普通库存的关系。
2. 场所秒杀联动真实场地时段库存。
3. 装备秒杀联动装备主库存。
4. 增加库存流水表，记录每一次扣减和回滚。

## 14. 总结

当前秒杀实现已经具备核心并发保护：

```text
Redis Lua 防 Redis 超卖
Redisson 防同一用户并发重复请求
RocketMQ 削峰异步落库
MySQL 条件扣库存防数据库超卖
MySQL 唯一索引兜底一人一单
```

因此它可以作为教学项目或中小并发演示使用。

但如果要向真实生产级秒杀靠近，还需要重点补强：

```text
可靠消息
启动预热保护
秒杀结果状态
Redis Cluster key 设计
场所/装备真实库存一致性
```
