package com.hm.badminton.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hm.badminton.entity.UserAccount;
import com.hm.badminton.mapper.UserMapper;
import com.hm.badminton.service.IBloomFilterService;
import com.hm.badminton.utils.RedisConstants;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class BloomWarmup implements ApplicationRunner {

    private final UserMapper userMapper;
    private final IBloomFilterService bloomFilterService;

    public BloomWarmup(UserMapper userMapper, IBloomFilterService bloomFilterService) {
        this.userMapper = userMapper;
        this.bloomFilterService = bloomFilterService;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (UserAccount user : userMapper.selectList(new QueryWrapper<UserAccount>().select("phone", "email", "username"))) {
            bloomFilterService.put(RedisConstants.BLOOM_USER_PHONE_KEY, user.getPhone());
            bloomFilterService.put(RedisConstants.BLOOM_USER_EMAIL_KEY, user.getEmail());
            bloomFilterService.put(RedisConstants.BLOOM_USER_USERNAME_KEY, user.getUsername());
        }
    }
}

