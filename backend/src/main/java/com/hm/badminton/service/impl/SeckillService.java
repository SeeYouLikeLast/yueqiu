package com.hm.badminton.service.impl;

import com.hm.badminton.common.BusinessException;
import com.hm.badminton.entity.SeckillActivity;
import com.hm.badminton.entity.SeckillOrder;
import com.hm.badminton.mapper.SeckillMapper;
import com.hm.badminton.service.ISeckillService;
import com.hm.badminton.utils.IdGenerator;
import com.hm.badminton.utils.RedisConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class SeckillService implements ISeckillService, ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeckillService.class);

    private final SeckillMapper seckillMapper;
    private final StringRedisTemplate redisTemplate;
    private final IdGenerator idGenerator;
    private final DefaultRedisScript<Long> seckillScript;

    public SeckillService(SeckillMapper seckillMapper, StringRedisTemplate redisTemplate, IdGenerator idGenerator) {
        this.seckillMapper = seckillMapper;
        this.redisTemplate = redisTemplate;
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

    public List<SeckillActivity> list(String sportCode, Long categoryId) {
        String normalizedSport = sportCode == null || sportCode.isBlank() ? null : sportCode.trim();
        return seckillMapper.selectActivities(normalizedSport, categoryId);
    }

    @Transactional
    public Long submit(Long userId, Long activityId) {
        SeckillActivity activity = getActivity(activityId);
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activity.startAt())) {
            throw new BusinessException("秒杀还未开始");
        }
        if (now.isAfter(activity.endAt())) {
            throw new BusinessException("秒杀已结束");
        }

        Long code = tryRedisPreDeduct(userId, activityId);
        if (code == null) {
            return submitWithDbLock(userId, activity);
        }
        if (code == 1) {
            throw new BusinessException("秒杀库存已抢完");
        }
        if (code == 2) {
            throw new BusinessException(409, "每人限购一件，请勿重复抢购");
        }
        return createSeckillOrder(userId, activity, false);
    }

    public SeckillOrder order(Long userId, Long orderId) {
        SeckillOrder order = seckillMapper.selectOrder(userId, orderId);
        if (order == null) {
            throw new BusinessException(404, "秒杀订单不存在");
        }
        return order;
    }

    public List<SeckillOrder> myOrders(Long userId) {
        return seckillMapper.selectOrdersByUser(userId);
    }

    @Transactional
    protected Long submitWithDbLock(Long userId, SeckillActivity activity) {
        Integer exists = seckillMapper.countUserActivityOrder(userId, activity.id());
        if (exists != null && exists > 0) {
            throw new BusinessException(409, "每人限购一件，请勿重复抢购");
        }
        int updated = seckillMapper.deductActivityStock(activity.id());
        if (updated == 0) {
            throw new BusinessException("秒杀库存已抢完");
        }
        return createSeckillOrder(userId, activity, true);
    }

    private Long createSeckillOrder(Long userId, SeckillActivity activity, boolean stockDeductedInDb) {
        long orderId = idGenerator.nextId();
        try {
            seckillMapper.insertOrder(orderId, activity.id(), activity.productId(), userId, activity.seckillPrice());
            if (!stockDeductedInDb) {
                seckillMapper.deductActivityStockLenient(activity.id());
            }
            seckillMapper.deductProductStockLenient(activity.productId());
            return orderId;
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, "每人限购一件，请勿重复抢购");
        }
    }

    private Long tryRedisPreDeduct(Long userId, Long activityId) {
        try {
            return redisTemplate.execute(seckillScript,
                    List.of(RedisConstants.SECKILL_STOCK_KEY + activityId, RedisConstants.SECKILL_USER_KEY + activityId),
                    String.valueOf(userId));
        } catch (Exception e) {
            log.warn("Redis 秒杀预扣失败，将降级到数据库扣减：{}", e.getMessage());
            return null;
        }
    }

    private SeckillActivity getActivity(Long activityId) {
        SeckillActivity activity = seckillMapper.selectActivity(activityId);
        if (activity == null) {
            throw new BusinessException(404, "秒杀活动不存在");
        }
        return activity;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            for (SeckillActivity activity : list(null, null)) {
                redisTemplate.opsForValue().set(RedisConstants.SECKILL_STOCK_KEY + activity.id(), String.valueOf(activity.stock()));
                redisTemplate.delete(RedisConstants.SECKILL_USER_KEY + activity.id());
            }
            log.info("秒杀库存已预热到 Redis");
        } catch (Exception e) {
            log.warn("Redis 不可用，秒杀将使用数据库降级方案：{}", e.getMessage());
        }
    }

    public Map<String, Object> preload() {
        run(null);
        return Map.of("message", "秒杀库存预热完成");
    }

}

