# 秒杀与 RocketMQ 异步落库

## 1. 功能

场馆商品和装备共用秒杀编排，数据表分开：

| type | 秒杀活动 | 秒杀订单 |
| --- | --- | --- |
| 1 | `seckill_venue` | `order_seckill_venue` |
| 2 | `seckill_equipment` | `order_seckill_equipment` |

## 2. HTTP 接口

| 方法 | 路径 | 登录 | 作用 |
| --- | --- | --- | --- |
| GET | `/api/seckill/{type}` | 否 | 秒杀活动列表，支持 `sport/categoryId` |
| POST | `/api/seckill/{type}/{seckillId}` | 是 | 提交秒杀，返回预生成订单 id |
| GET | `/api/orders/{type}` | 是 | 普通与秒杀订单统一查询 |

## 3. 请求链路

```text
活动时间校验
 -> Redisson 用户级分布式锁
 -> Redis Lua 原子预扣库存 + 记录已购用户
 -> RocketMQ 发送订单消息
 -> Consumer 解析并幂等消费
 -> Service 事务扣 MySQL 库存 + 写秒杀订单
```

ECS `prod,lite` 模式可不启动 RocketMQ，请求在 Lua 预扣后同步调用同一落库服务。

## 4. 每层保护什么

### Redisson 锁

Key：`lock:seckill:order:{type}:{activityId}:{userId}`。

解决同一用户在同一时刻重复点击、多线程和多实例并发。锁只是入口优化，不代替 Redis 已购集合和 MySQL 唯一索引。

### Redis Lua

Lua 在 Redis 单线程内原子执行：

1. 检查库存是否大于 0。
2. 检查用户是否已在已购 Set。
3. 库存减 1。
4. 用户加入 Set。

不把四个操作拆成 Java 多次 Redis 请求，避免中间状态被其他请求插入。

### RocketMQ

- 请求线程不等待 MySQL 全部写入，用于削峰。
- 消费者在独立事务中落库。
- 消费失败可由 RocketMQ 重试；超过次数进入死信处理范围。

### MySQL

- 条件扣库存是最终不超卖保护。
- `user_id + seckill_id` 唯一索引是最终一人一单保护。
- 消费者按预生成 orderId 幂等。

## 5. 失败补偿

- MQ 投递失败且允许同步降级时，直接同步落库。
- 投递/同步落库都失败时，回滚 Redis 库存和已购标记。
- 消息已投递但消费结果未知时，依赖消息重试 + 数据库幂等，不能盲目回补造成双花。

## 6. 热点缓存

`seckill:activity:{type}:{activityId}` 使用逻辑过期：热点过期时返回旧值，通过 Redis 锁只让一个线程异步重建。秒杀时间和真实库存仍会在提交链路再校验。

## 7. 当前限制

- 返回 orderId 不表示异步订单已经完全可查，生产系统应增加订单状态查询/轮询。
- 同步降级保证功能可用，但失去 MQ 削峰能力。
- 不应把 Redis 预扣当成最终订单成功。

## 8. 代码入口

`SeckillController`、`SeckillService`、`seckill.lua`、`SeckillOrderConsumer`、`SeckillOrderMessageService`、`SeckillMapper`。
