package com.hm.badminton.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(@Value("${spring.data.redis.host:localhost}") String host,
                                         @Value("${spring.data.redis.port:6379}") int port,
                                         @Value("${spring.data.redis.password:}") String password) {
        Config config = new Config();
        var server = config.useSingleServer()
                .setAddress("redis://" + host + ":" + port);
        if (password != null && !password.isBlank()) {
            server.setPassword(password);
        }
        return Redisson.create(config);
    }
}

