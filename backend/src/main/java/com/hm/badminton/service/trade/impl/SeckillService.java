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
import com.hm.badminton.service.trade.ISeckillService;
import com.hm.badminton.utils.IdGenerator;
import com.hm.badminton.utils.RedisTtl;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
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
    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;
    private final IdGenerator idGenerator;
    private final DefaultRedisScript<Long> seckillScript;

    public SeckillService(SeckillMapper seckillMapper,
                          StringRedisTemplate redisTemplate,
                          RedissonClient redissonClient,
                          RocketMQTemplate rocketMQTemplate,
                          ObjectMapper objectMapper,
                          IdGenerator idGenerator) {
        this.seckillMapper = seckillMapper;
        this.redisTemplate = redisTemplate;
        this.redissonClient = redissonClient;
        this.rocketMQTemplate = rocketMQTemplate;
        this.objectMapper = objectMapper;
        this.idGenerator = idGenerator;
        this.seckillScript = new DefaultRedisScript<>("""
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
                """, Long.class);
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
        int tradeType = TradeType.require(type);
        SeckillActivity activity = getActivity(tradeType, activityId);
        validateActivityTime(activity);

        RLock lock = redissonClient.getLock(RedisConstants.SECKILL_ORDER_LOCK_KEY + tradeType + ":" + activityId + ":" + userId);
        if (!tryUserLock(lock)) {
            throw new BusinessException(409, "不能重复抢购");
        }

        boolean preDeducted = false;
        try {
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

            long orderId = idGenerator.nextId();
            sendSeckillOrderMessage(new SeckillOrderMessage(
                    orderId,
                    tradeType,
                    activity.getId(),
                    activity.getProductId(),
                    userId,
                    activity.getSeckillPrice()));
            return orderId;
        } catch (RuntimeException e) {
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

    private void sendSeckillOrderMessage(SeckillOrderMessage message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            rocketMQTemplate.syncSend(MqConstants.SECKILL_ORDER_TOPIC, payload, 3000);
        } catch (JsonProcessingException e) {
            log.warn("秒杀订单消息序列化失败, orderId={}: {}", message.getOrderId(), e.getMessage());
            throw new BusinessException(503, "秒杀下单失败，请稍后再试");
        } catch (Exception e) {
            log.warn("秒杀订单消息发送失败, orderId={}: {}", message.getOrderId(), e.getMessage());
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
        SeckillActivity activity = type == TradeType.VENUE
                ? seckillMapper.selectVenueActivity(activityId)
                : seckillMapper.selectEquipmentActivity(activityId);
        if (activity == null) {
            throw new BusinessException(404, "秒杀活动不存在");
        }
        return activity;
    }

    private void preloadActivity(SeckillActivity activity, List<Long> userIds) {
        String stockKey = stockKey(activity.getType(), activity.getId());
        String userKey = userKey(activity.getType(), activity.getId());
        Duration ttl = seckillCacheTtl(activity);

        redisTemplate.opsForValue().set(stockKey, String.valueOf(activity.getStock()), ttl);
        redisTemplate.delete(userKey);
        if (!userIds.isEmpty()) {
            redisTemplate.opsForSet().add(userKey, userIds.stream().map(String::valueOf).toArray(String[]::new));
        }
        redisTemplate.expire(userKey, ttl);
    }

    private void deleteLegacyActivityKeys(Long activityId) {
        redisTemplate.delete(List.of(
                RedisConstants.SECKILL_STOCK_KEY + activityId,
                RedisConstants.SECKILL_USER_KEY + activityId
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
