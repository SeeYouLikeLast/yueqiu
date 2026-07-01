package com.hm.badminton.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hm.badminton.dto.BlogView;
import com.hm.badminton.entity.Follow;
import com.hm.badminton.entity.UserAccount;
import com.hm.badminton.mapper.FollowMapper;
import com.hm.badminton.mapper.UserMapper;
import com.hm.badminton.service.IFollowService;
import com.hm.badminton.utils.RedisConstants;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class FollowService extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    private final StringRedisTemplate redisTemplate;
    private final UserMapper userMapper;

    public FollowService(StringRedisTemplate redisTemplate, UserMapper userMapper) {
        this.redisTemplate = redisTemplate;
        this.userMapper = userMapper;
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
            Follow record = new Follow();
            record.setUserId(userId);
            record.setFollowUserId(followUserId);
            save(record);
            redisTemplate.opsForSet().add(key, String.valueOf(followUserId));
        } else {
            remove(new LambdaQueryWrapper<Follow>()
                    .eq(Follow::getUserId, userId)
                    .eq(Follow::getFollowUserId, followUserId));
            redisTemplate.opsForSet().remove(key, String.valueOf(followUserId));
        }
    }

    @Override
    public List<BlogView> commonFollow(Long userId, Long targetUserId) {
        String key1 = RedisConstants.FOLLOW_KEY + userId;
        String key2 = RedisConstants.FOLLOW_KEY + targetUserId;
        Set<String> common = redisTemplate.opsForSet().intersect(key1, key2);
        if (common == null || common.isEmpty()) {
            common = loadFollowIds(userId);
            Set<String> targetFollows = loadFollowIds(targetUserId);
            common.retainAll(targetFollows);
        }
        if (common.isEmpty()) {
            return Collections.emptyList();
        }
        return common.stream()
                .map(Long::valueOf)
                .map(userMapper::selectById)
                .filter(Objects::nonNull)
                .map(this::toUserCard)
                .toList();
    }

    private BlogView toUserCard(UserAccount user) {
        return new BlogView(user.getId(), user.getId(), user.getNickname(), user.getAvatar(), null,
                user.getNickname(), null, Collections.emptyList(), null, null, null, null, null,
                0, false, true, user.getCreatedAt());
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
