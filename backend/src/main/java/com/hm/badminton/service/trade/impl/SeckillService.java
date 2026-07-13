package com.hm.badminton.service.trade.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.badminton.common.BusinessException;
import com.hm.badminton.constants.MqConstants;
import com.hm.badminton.constants.RedisConstants;
import com.hm.badminton.constants.TradeType;
import com.hm.badminton.dto.SeckillOrderMessage;
import com.hm.badminton.entity.SeckillActivity;
import com.hm.badminton.entity.SeckillOrder;
import com.hm.badminton.mapper.trade.SeckillMapper;
import com.hm.badminton.mq.SeckillOrderMessageService;
import com.hm.badminton.service.trade.ISeckillService;
import com.hm.badminton.utils.CacheClient;
import com.hm.badminton.utils.IdGenerator;
import com.hm.badminton.utils.RedisTtl;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class SeckillService implements ISeckillService, ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeckillService.class);
    private static final Duration SECKILL_KEY_KEEP_AFTER_END = Duration.ofHours(1);
    private static final long SECKILL_KEY_JITTER_SECONDS = Duration.ofMinutes(30).toSeconds();

    private final SeckillMapper seckillMapper;
    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;
    private final ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider;
    private final SeckillOrderMessageService orderMessageService;
    private final ObjectMapper objectMapper;
    private final IdGenerator idGenerator;
    private final DefaultRedisScript<Long> seckillScript;
    private final CacheClient cacheClient;
    private final boolean mqEnabled;
    private final boolean syncFallbackEnabled;

    public SeckillService(SeckillMapper seckillMapper,
                          StringRedisTemplate redisTemplate,
                          RedissonClient redissonClient,
                           ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider,
                           SeckillOrderMessageService orderMessageService,
                          ObjectMapper objectMapper,
                          IdGenerator idGenerator,
                          DefaultRedisScript<Long> seckillScript,
                           CacheClient cacheClient,
                           @Value("${hm.seckill.mq-enabled:true}") boolean mqEnabled,
                           @Value("${hm.seckill.sync-fallback-enabled:true}") boolean syncFallbackEnabled) {
        this.seckillMapper = seckillMapper;
        this.redisTemplate = redisTemplate;
        this.redissonClient = redissonClient;
        this.rocketMQTemplateProvider = rocketMQTemplateProvider;
        this.orderMessageService = orderMessageService;
        this.objectMapper = objectMapper;
        this.idGenerator = idGenerator;
        this.seckillScript = seckillScript;
        this.cacheClient = cacheClient;
        this.mqEnabled = mqEnabled;
        this.syncFallbackEnabled = syncFallbackEnabled;
    }

    @Override
    public List<SeckillActivity> list(Integer type, String sportCode, Long categoryId) {
        String normalizedSport = sportCode == null || sportCode.isBlank() ? null : sportCode.trim();
        int tradeType = TradeType.require(type);
        if (tradeType == TradeType.VENUE) {
            return seckillMapper.selectVenueActivities(normalizedSport);
        }
        return seckillMapper.selectEquipmentActivities(normalizedSport, categoryId);
    }

    @Override
    public Long submit(Long userId, Integer type, Long activityId) {
        // 1. 校验业务类型、加载活动详情，并判断秒杀是否处于可购买时间窗口。
        int tradeType = TradeType.require(type);
        SeckillActivity activity = getActivity(tradeType, activityId);
        validateActivityTime(activity);

        // 如果这个用户同一时间点连点按钮、开多个浏览器窗口、或者请求被重复发送，只有第一个请求能拿到锁。
        // SETNX + EXPIRE 也可以实现一人一单，但 Redisson 的 RLock 更安全，避免了锁过期后被其他线程误解锁的风险。
        RLock lock = redissonClient.getLock(RedisConstants.SECKILL_ORDER_LOCK_KEY + tradeType + ":" + activityId + ":" + userId);
        if (!tryUserLock(lock)) {
            throw new BusinessException(409, "不能重复抢购");
        }

        // 2. 提前生成订单号。优先投递 RocketMQ；MQ 不可用时可降级到同一套事务落库服务。
        long orderId = idGenerator.nextId();
        SeckillOrderMessage orderMessage = new SeckillOrderMessage(
                orderId,
                tradeType,
                activity.getId(),
                activity.getProductId(),
                userId,
                activity.getSeckillPrice());

        boolean preDeducted = false;
        try {
            // Lua 在 Redis 内原子完成库存预扣和一人一单标记。
            Long code = tryRedisPreDeduct(userId, tradeType, activityId);
            if (code == null) {
                throw new BusinessException(503, "秒杀服务繁忙，请稍后再试");
            }
            if (code == 1) {
                throw new BusinessException("库存不足");
            }
            if (code == 2) {
                throw new BusinessException(409, "不能重复抢购");
            }
            preDeducted = true;

            // 4. 优先异步落库；轻量部署或 MQ 故障时同步落库，保证秒杀请求仍可完成。
            createSeckillOrder(orderMessage);
            return orderId;
        } catch (RuntimeException e) {
            // 5. 异步投递或同步落库失败时回滚 Redis 预扣，避免库存和已购集合长期不一致。
            if (preDeducted) {
                rollbackRedisPreDeduct(userId, tradeType, activityId);
            }
            throw e;
        } finally {
            unlockQuietly(lock);
        }
    }

    @Override
    public List<SeckillOrder> myOrders(Long userId, Integer type) {
        int tradeType = TradeType.require(type);
        return tradeType == TradeType.VENUE
                ? seckillMapper.selectVenueOrdersByUser(userId)
                : seckillMapper.selectEquipmentOrdersByUser(userId);
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            // 应用启动时把数据库里的秒杀库存和已下单用户预热到 Redis，Lua 才能全内存判断。
            for (SeckillActivity activity : list(TradeType.EQUIPMENT, null, null)) {
                deleteLegacyActivityKeys(activity.getId());
                preloadActivity(activity, seckillMapper.selectEquipmentUserIdsByActivity(activity.getId()));
            }
            for (SeckillActivity activity : list(TradeType.VENUE, null, null)) {
                deleteLegacyActivityKeys(activity.getId());
                preloadActivity(activity, seckillMapper.selectVenueUserIdsByActivity(activity.getId()));
            }
            log.info("秒杀库存和已购用户集合已预热到 Redis");
        } catch (Exception e) {
            log.warn("秒杀 Redis 预热失败: {}", e.getMessage());
        }
    }

    private void validateActivityTime(SeckillActivity activity) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activity.getStartAt())) {
            throw new BusinessException("秒杀尚未开始");
        }
        if (now.isAfter(activity.getEndAt())) {
            throw new BusinessException("秒杀已结束");
        }
    }

    private boolean tryUserLock(RLock lock) {
        try {
            return lock.tryLock(0, 3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(503, "秒杀服务繁忙，请稍后再试");
        } catch (Exception e) {
            log.warn("Redisson 一人一单加锁失败: {}", e.getMessage());
            throw new BusinessException(503, "秒杀服务繁忙，请稍后再试");
        }
    }

    private void unlockQuietly(RLock lock) {
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (Exception e) {
            log.warn("Redisson 解锁失败: {}", e.getMessage());
        }
    }

    private String serializeSeckillMessage(SeckillOrderMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            log.error("秒杀订单序列化失败, orderId={}: {}", message.getOrderId(), e.getMessage());
            throw new BusinessException(503, "秒杀下单失败，请稍后再试");
        }
    }

    private void createSeckillOrder(SeckillOrderMessage message) {
        if (mqEnabled) {
            RocketMQTemplate rocketMQTemplate = rocketMQTemplateProvider.getIfAvailable();
            if (rocketMQTemplate != null) {
                try {
                    sendSeckillOrderMessage(rocketMQTemplate, serializeSeckillMessage(message));
                    return;
                } catch (BusinessException e) {
                    if (!syncFallbackEnabled) {
                        throw e;
                    }
                    // Broker 可能已收到但客户端未拿到确认。同步落库使用同一订单号，后续消费者会幂等忽略重复消息。
                    log.warn("RocketMQ 投递失败，降级同步落库, orderId={}, reason={}", message.getOrderId(), e.getMessage());
                }
            } else if (!syncFallbackEnabled) {
                throw new BusinessException(503, "RocketMQ 未启用，无法异步创建秒杀订单");
            } else {
                log.info("RocketMQ 未部署，使用同步秒杀落库, orderId={}", message.getOrderId());
            }
        }
        orderMessageService.createOrder(message);
        log.info("秒杀订单已同步落库, orderId={}", message.getOrderId());
    }

    private void sendSeckillOrderMessage(RocketMQTemplate rocketMQTemplate, String payload) {
        try {
            rocketMQTemplate.syncSend(MqConstants.SECKILL_ORDER_TOPIC, payload, 3000);
        } catch (Exception e) {
            log.warn("秒杀订单消息发送失败: {}", e.getMessage());
            throw new BusinessException(503, "秒杀下单失败，请稍后再试");
        }
    }

    private Long tryRedisPreDeduct(Long userId, int type, Long activityId) {
        try {
            return redisTemplate.execute(seckillScript,
                    List.of(stockKey(type, activityId), userKey(type, activityId)),
                    String.valueOf(userId));
        } catch (Exception e) {
            log.warn("Redis 秒杀预扣失败: {}", e.getMessage());
            return null;
        }
    }

    private void rollbackRedisPreDeduct(Long userId, int type, Long activityId) {
        try {
            redisTemplate.opsForValue().increment(stockKey(type, activityId));
            redisTemplate.opsForSet().remove(userKey(type, activityId), String.valueOf(userId));
        } catch (Exception e) {
            log.warn("Redis 秒杀预扣回滚失败, userId={}, activityId={}: {}", userId, activityId, e.getMessage());
        }
    }

    private SeckillActivity getActivity(int type, Long activityId) {
        return cacheClient.queryWithLogicalExpire(
                activityMetaKey(type, activityId),
                SeckillActivity.class,
                () -> type == TradeType.VENUE
                        ? seckillMapper.selectVenueActivity(activityId)
                        : seckillMapper.selectEquipmentActivity(activityId),
                RedisConstants.CACHE_LOGICAL_TTL,
                "秒杀活动不存在");
    }

    private void cacheActivityMeta(SeckillActivity activity) {
        cacheClient.setWithLogicalExpire(activityMetaKey(activity.getType(), activity.getId()),
                activity,
                RedisConstants.CACHE_LOGICAL_TTL);
    }

    private String activityMetaKey(int type, Long activityId) {
        return RedisConstants.SECKILL_ACTIVITY_KEY + type + ":" + activityId;
    }

    private void preloadActivity(SeckillActivity activity, List<Long> userIds) {
        String stockKey = stockKey(activity.getType(), activity.getId());
        String userKey = userKey(activity.getType(), activity.getId());
        Duration ttl = seckillCacheTtl(activity);

        // 1. 库存 key 保存剩余可抢数量，TTL 覆盖活动结束后一段时间，便于查订单和防重复。
        redisTemplate.opsForValue().set(stockKey, String.valueOf(activity.getStock()), ttl);
        // 2. 用户集合保存已经抢到的人，用于 Lua 一人一单判断。
        redisTemplate.delete(userKey);
        if (!userIds.isEmpty()) {
            redisTemplate.opsForSet().add(userKey, userIds.stream().map(String::valueOf).toArray(String[]::new));
        }
        redisTemplate.expire(userKey, ttl);

        // 3. 预热活动元数据，让 submit 优先读逻辑过期缓存，降低热点活动打到数据库的概率。
        cacheActivityMeta(activity);
    }

    private void deleteLegacyActivityKeys(Long activityId) {
        redisTemplate.delete(List.of(
                RedisConstants.SECKILL_STOCK_KEY + activityId,
                RedisConstants.SECKILL_USER_KEY + activityId,
                RedisConstants.SECKILL_ACTIVITY_KEY + activityId
        ));
    }

    private String stockKey(int type, Long activityId) {
        return RedisConstants.SECKILL_STOCK_KEY + type + ":" + activityId;
    }

    private String userKey(int type, Long activityId) {
        return RedisConstants.SECKILL_USER_KEY + type + ":" + activityId;
    }

    private Duration seckillCacheTtl(SeckillActivity activity) {
        Duration untilEnd = Duration.between(LocalDateTime.now(), activity.getEndAt());
        Duration base = untilEnd.isNegative() || untilEnd.isZero()
                ? SECKILL_KEY_KEEP_AFTER_END
                : untilEnd.plus(SECKILL_KEY_KEEP_AFTER_END);
        return RedisTtl.withJitter(base, SECKILL_KEY_JITTER_SECONDS);
    }
}
