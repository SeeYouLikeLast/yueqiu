package com.hm.badminton.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.badminton.common.BusinessException;
import com.hm.badminton.constants.RedisConstants;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Redis 缓存模板，集中实现三种常见策略。
 *
 * <ul>
 *   <li>旁路缓存 + 空值：适合普通详情，防止不存在 id 反复打到 MySQL。</li>
 *   <li>逻辑过期：适合极少数热点 key，过期时先返回旧值并异步重建，防止击穿。</li>
 *   <li>普通短缓存：适合高德 API 和低频变化列表，并支持随机 TTL 防雪崩。</li>
 * </ul>
 */
@Component
public class CacheClient {

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(4);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public CacheClient(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 缓存穿透保护：数据库查不到时写入短 TTL 空值，挡住重复访问不存在 id 的请求。
     */
    public <T> T queryWithPassThrough(String key,
                                      Class<T> type,
                                      Supplier<T> dbFallback,
                                      Duration ttl,
                                      long jitterSeconds,
                                      String notFoundMessage) {
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            if (RedisConstants.CACHE_NULL_VALUE.equals(cached)) {
                throw new BusinessException(404, notFoundMessage);
            }
            try {
                return objectMapper.readValue(cached, type);
            } catch (Exception e) {
                redisTemplate.delete(key);
            }
        }

        T value = dbFallback.get();
        if (value == null) {
            cacheNull(key);
            throw new BusinessException(404, notFoundMessage);
        }
        set(key, value, ttl, jitterSeconds);
        return value;
    }

    /**
     * 缓存击穿保护：热点 key 命中但逻辑过期时，先返回旧数据，再由拿到锁的线程异步重建缓存。
     */
    public <T> T queryWithLogicalExpire(String key,
                                        Class<T> type,
                                        Supplier<T> dbFallback,
                                        Duration logicalTtl,
                                        String notFoundMessage) {
        String cached = getString(key);
        if (cached != null) {
            if (RedisConstants.CACHE_NULL_VALUE.equals(cached)) {
                throw new BusinessException(404, notFoundMessage);
            }
            LogicalCacheData<T> cacheData = parseLogicalCache(cached, type);
            if (cacheData != null) {
                if (cacheData.getExpireTime().isAfter(LocalDateTime.now())) {
                    return cacheData.getData();
                }
                tryRebuild(key, dbFallback, logicalTtl);
                return cacheData.getData();
            }
            redisTemplate.delete(key);
        }

        T value = dbFallback.get();
        if (value == null) {
            cacheNull(key);
            throw new BusinessException(404, notFoundMessage);
        }
        setWithLogicalExpire(key, value, logicalTtl);
        return value;
    }

    /**
     * 普通短缓存：适合第三方 API 结果这类非数据库详情数据。fallback 返回 null 时不写缓存。
     */
    public <T> T querySimple(String key, TypeReference<T> type, Supplier<T> fallback, Duration ttl) {
        return querySimple(key, type, fallback, ttl, 0);
    }

    /**
     * 带随机 TTL 的普通短缓存。
     * 第三方接口的热门查询会在同一时段大量过期，抖动可以避免缓存同时失效后集中回源。
     */
    public <T> T querySimple(String key,
                             TypeReference<T> type,
                             Supplier<T> fallback,
                             Duration ttl,
                             long jitterSeconds) {
        String cached = getString(key);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, type);
            } catch (Exception e) {
                redisTemplate.delete(key);
            }
        }
        T value = fallback.get();
        if (value != null) {
            set(key, value, ttl, jitterSeconds);
        }
        return value;
    }

    public void setWithLogicalExpire(String key, Object value, Duration logicalTtl) {
        LogicalCacheWriteData cacheData = new LogicalCacheWriteData();
        cacheData.setData(value);
        cacheData.setExpireTime(LocalDateTime.now().plus(logicalTtl));
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(cacheData));
        } catch (JsonProcessingException e) {
            throw new BusinessException(503, "缓存写入失败");
        }
    }

    public void cacheNull(String key) {
        redisTemplate.opsForValue().set(key, RedisConstants.CACHE_NULL_VALUE, RedisConstants.CACHE_NULL_TTL);
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }

    private String getString(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            redisTemplate.delete(key);
            return null;
        }
    }

    private <T> LogicalCacheData<T> parseLogicalCache(String cached, Class<T> type) {
        try {
            LogicalCacheReadData readData = objectMapper.readValue(cached, LogicalCacheReadData.class);
            LogicalCacheData<T> result = new LogicalCacheData<>();
            result.setData(objectMapper.convertValue(readData.getData(), type));
            result.setExpireTime(readData.getExpireTime());
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    private <T> void tryRebuild(String key, Supplier<T> dbFallback, Duration logicalTtl) {
        String lockKey = RedisConstants.CACHE_REBUILD_LOCK_KEY + key;
        String lockValue = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, RedisConstants.CACHE_REBUILD_LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }
        CACHE_REBUILD_EXECUTOR.submit(() -> {
            try {
                T fresh = dbFallback.get();
                if (fresh == null) {
                    cacheNull(key);
                } else {
                    setWithLogicalExpire(key, fresh, logicalTtl);
                }
            } finally {
                unlock(lockKey, lockValue);
            }
        });
    }

    private void unlock(String lockKey, String lockValue) {
        String current = redisTemplate.opsForValue().get(lockKey);
        if (lockValue.equals(current)) {
            redisTemplate.delete(lockKey);
        }
    }

    private void set(String key, Object value, Duration ttl, long jitterSeconds) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), RedisTtl.withJitter(ttl, jitterSeconds));
        } catch (JsonProcessingException e) {
            throw new BusinessException(503, "缓存写入失败");
        }
    }

    @Data
    @NoArgsConstructor
    private static class LogicalCacheReadData {
        private JsonNode data;
        private LocalDateTime expireTime;
    }

    @Data
    @NoArgsConstructor
    private static class LogicalCacheWriteData {
        private Object data;
        private LocalDateTime expireTime;
    }

    @Data
    @NoArgsConstructor
    private static class LogicalCacheData<T> {
        private T data;
        private LocalDateTime expireTime;
    }
}
