package com.hm.badminton.service.auth.impl;

import com.hm.badminton.service.auth.IBloomFilterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class BloomFilterService implements IBloomFilterService {

    private static final Logger log = LoggerFactory.getLogger(BloomFilterService.class);

    private final StringRedisTemplate redisTemplate;
    private final long bitSize;
    private final int hashCount;

    public BloomFilterService(StringRedisTemplate redisTemplate,
                              @Value("${hm.bloom.bit-size}") long bitSize,
                              @Value("${hm.bloom.hash-count}") int hashCount) {
        this.redisTemplate = redisTemplate;
        this.bitSize = bitSize;
        this.hashCount = hashCount;
    }

    public Boolean mightContain(String key, String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            for (int i = 0; i < hashCount; i++) {
                Boolean bit = redisTemplate.opsForValue().getBit(key, hash(value, i));
                if (!Boolean.TRUE.equals(bit)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            log.warn("Redis 布隆过滤器读取失败，将降级为数据库精确查询：{}", e.getMessage());
            return null;
        }
    }

    public void put(String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        try {
            for (int i = 0; i < hashCount; i++) {
                redisTemplate.opsForValue().setBit(key, hash(value, i), true);
            }
        } catch (Exception e) {
            log.warn("Redis 布隆过滤器写入失败：{}", e.getMessage());
        }
    }

    private long hash(String value, int seed) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((seed + ":" + value).getBytes(StandardCharsets.UTF_8));
            long result = 0;
            for (int i = 0; i < 8; i++) {
                result = (result << 8) | (bytes[i] & 0xffL);
            }
            return Math.floorMod(result, bitSize);
        } catch (Exception e) {
            throw new IllegalStateException("Hash 计算失败", e);
        }
    }
}



