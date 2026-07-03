package com.hm.badminton.interceptor;

import com.hm.badminton.dto.LoginUser;
import com.hm.badminton.constants.RedisConstants;
import com.hm.badminton.utils.RedisTtl;
import com.hm.badminton.utils.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.Map;

@Component
public class RefreshTokenInterceptor implements HandlerInterceptor {

    private final UserContext userContext;
    private final StringRedisTemplate redisTemplate;
    private final Duration tokenTtl;
    private final long tokenTtlJitterMaxSeconds;

    public RefreshTokenInterceptor(UserContext userContext,
                                   StringRedisTemplate redisTemplate,
                                   @Value("${hm.auth.token-expire-minutes:120}") long tokenExpireMinutes,
                                   @Value("${hm.auth.token-expire-jitter-minutes:10}") long tokenExpireJitterMinutes) {
        this.userContext = userContext;
        this.redisTemplate = redisTemplate;
        this.tokenTtl = Duration.ofMinutes(tokenExpireMinutes);
        this.tokenTtlJitterMaxSeconds = Duration.ofMinutes(tokenExpireJitterMinutes).toSeconds();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = bearerToken(request);
        if (token == null) {
            return true;
        }

        String key = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        if (entries.isEmpty()) {
            return true;
        }

        userContext.set(new LoginUser(
                Long.valueOf(value(entries, "id")),
                null,
                null,
                value(entries, "city"),
                null,
                doubleValue(entries, "lng"),
                doubleValue(entries, "lat"),
                null));
        redisTemplate.expire(key, RedisTtl.withJitter(tokenTtl, tokenTtlJitterMaxSeconds));
        return true;
    }

    private String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private String value(Map<Object, Object> entries, String key) {
        Object value = entries.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Double doubleValue(Map<Object, Object> entries, String key) {
        String value = value(entries, key);
        if (value == null || value.isBlank()) {
            return null;
        }
        return Double.valueOf(value);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        userContext.clear();
    }
}

