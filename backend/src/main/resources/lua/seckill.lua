-- KEYS[1]：活动库存 String；KEYS[2]：已购用户 Set；ARGV[1]：当前 userId。
-- 整个脚本由 Redis 单线程一次执行完，中间不会被其他请求插入，因此检查与扣减是原子的。
local stock = tonumber(redis.call('get', KEYS[1]) or '0')
if stock <= 0 then
  return 1 -- 库存不足
end
if redis.call('sismember', KEYS[2], ARGV[1]) == 1 then
  return 2 -- 当前用户已经抢过
end
redis.call('decr', KEYS[1])
redis.call('sadd', KEYS[2], ARGV[1])
return 0 -- 预扣成功，Java 可以继续投递订单消息
