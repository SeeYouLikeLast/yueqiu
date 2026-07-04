package com.hm.badminton.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.badminton.common.BusinessException;
import com.hm.badminton.constants.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

@Component
public class CacheClient {

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

    public void cacheNull(String key) {
        redisTemplate.opsForValue().set(key, RedisConstants.CACHE_NULL_VALUE, RedisConstants.CACHE_NULL_TTL);
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }

    private void set(String key, Object value, Duration ttl, long jitterSeconds) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), RedisTtl.withJitter(ttl, jitterSeconds));
        } catch (JsonProcessingException e) {
            throw new BusinessException(503, "缓存写入失败");
        }
    }
}
