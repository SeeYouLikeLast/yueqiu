package com.hm.badminton.service.community.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hm.badminton.entity.Follow;
import com.hm.badminton.mapper.community.FollowMapper;
import com.hm.badminton.service.community.IFollowService;
import com.hm.badminton.constants.RedisConstants;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;

@Service
public class FollowService extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    private final StringRedisTemplate redisTemplate;

    public FollowService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean isFollowed(Long userId, Long followUserId) {
        if (userId == null || followUserId == null) return false;
        String key = RedisConstants.FOLLOW_KEY + userId;
        Boolean cached = redisTemplate.opsForSet().isMember(key, String.valueOf(followUserId));
        if (Boolean.TRUE.equals(cached)) return true;
        boolean exists = count(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getUserId, userId)
                .eq(Follow::getFollowUserId, followUserId)) > 0;
        if (exists) {
            redisTemplate.opsForSet().add(key, String.valueOf(followUserId));
        }
        return exists;
    }

    @Override
    @Transactional
    public void follow(Long userId, Long followUserId, boolean follow) {
        if (Objects.equals(userId, followUserId)) {
            return;
        }
        String key = RedisConstants.FOLLOW_KEY + userId;
        if (follow) {
            if (isFollowed(userId, followUserId)) {
                return;
            }
            Follow followRecord = new Follow();
            followRecord.setUserId(userId);
            followRecord.setFollowUserId(followUserId);
            save(followRecord);
            redisTemplate.opsForSet().add(key, String.valueOf(followUserId));
        } else {
            remove(new LambdaQueryWrapper<Follow>()
                    .eq(Follow::getUserId, userId)
                    .eq(Follow::getFollowUserId, followUserId));
            redisTemplate.opsForSet().remove(key, String.valueOf(followUserId));
        }
    }

    private Set<String> loadFollowIds(Long userId) {
        String key = RedisConstants.FOLLOW_KEY + userId;
        Set<String> ids = list(new LambdaQueryWrapper<Follow>().eq(Follow::getUserId, userId)).stream()
                .map(Follow::getFollowUserId)
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.toSet());
        if (!ids.isEmpty()) {
            redisTemplate.opsForSet().add(key, ids.toArray(String[]::new));
        }
        return ids;
    }
}


