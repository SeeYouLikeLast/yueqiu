# Redis 缓存与高并发保护

## 1. Redis 职责

Redis 不是 MySQL 的替代品。本项目中它承担：

- token 登录态和验证码。
- 布隆过滤器。
- 附近场所、逆地理编码和列表短缓存。
- 详情缓存和空值缓存。
- 秒杀库存、已购用户集合、分布式锁。
- 关注 Feed、点赞和关注集合。
- AI 结构化记忆、游客历史、Embedding 和限流。

## 2. CacheClient 能力

### `querySimple`

标准 Cache Aside：

1. 查 Redis。
2. 命中正常值直接返回。
3. 命中 `__NULL__` 返回空。
4. 未命中查数据源。
5. 空结果写 2 分钟空值，正常结果写业务 TTL。

### `queryWithLogicalExpire`

只用于高热点秒杀活动：

1. Redis 值包含数据和逻辑过期时间。
2. 未过期直接返回。
3. 已过期仍返回旧值，保证快速响应。
4. 用 `lock:cache:rebuild:*` 争抢重建权。
5. 只有一个线程异步查 MySQL 并刷新缓存。

## 3. 穿透、雪崩、击穿

| 问题 | 项目解法 |
| --- | --- |
| 缓存穿透 | 空值缓存；用户唯一性使用布隆预检 |
| 缓存雪崩 | TTL 增加随机抖动，token 也使用抖动 |
| 缓存击穿 | 秒杀活动使用逻辑过期 + 重建锁 |
| 重复请求 | Redisson 用户锁、Redis Set、MySQL 唯一索引 |
| 高并发扣减 | Redis Lua 入口预扣 + MySQL 条件 UPDATE 兜底 |

装备详情和场馆商品详情只使用普通缓存，不滥用逻辑过期，避免复杂度超过收益。

## 4. 主要 Key

| 前缀 | 数据结构 | 用途 |
| --- | --- | --- |
| `login:token:` | Hash | 轻量登录态 |
| `login:code:email:` | String | 邮箱验证码 |
| `bf:user:*` | Bitmap | 用户唯一性布隆过滤器 |
| `amap:nearby:` | String/JSON | 附近场所 |
| `equipment:` | String/JSON | 装备详情 |
| `venue:item:` | String/JSON | 场馆商品详情 |
| `seckill:activity:` | 逻辑过期 JSON | 秒杀活动热点 |
| `seckill:stock:` | String | 秒杀库存 |
| `seckill:users:` | Set | 已购用户 |
| `feed:` | ZSet | 关注流时间线 |
| `blog:liked:` | ZSet | 点赞用户及时间 |
| `agent:*` | String/List/ZSet | AI 记忆、历史、RAG 和限流 |

## 5. 更新策略

- 查询使用 Cache Aside。
- 写操作先完成 MySQL 事务，再删除相关缓存。
- 对列表类模糊 Key，当前使用按前缀清理；大规模生产中应改为版本号/精确索引，避免 `KEYS`。
- Redis 不可用时，普通查询应可回源 MySQL；秒杀不能无保护直接压数据库。

## 6. 监控指标

- 缓存命中率、空值命中率。
- Redis 命令 P95/P99、连接池等待。
- 重建锁获取失败数和重建耗时。
- 秒杀 Lua 返回码分布。
- big key、hot key、内存占用和淘汰数。

## 7. 代码入口

`RedisConstants`、`CacheClient`、`RedisTtl`、`RedisScriptConfig`、`RedissonConfig`、`RefreshTokenInterceptor`。
