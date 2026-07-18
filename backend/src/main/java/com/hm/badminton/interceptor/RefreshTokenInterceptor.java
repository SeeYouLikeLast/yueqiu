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

/**
 * Token 刷新拦截器，对所有请求执行但不强制登录。
 *
 * <p>Token 本身只是随机字符串，真实登录态保存在 Redis Hash 中。这里根据请求头读取 Hash，
 * 只恢复当前请求真正需要的用户 id、城市和坐标，并采用滑动过期续期。请求结束后必须清理
 * ThreadLocal，防止线程池复用时把上一位用户带到下一次请求。</p>
 */
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
        // 1. 公开请求可以不带 Token；本拦截器只负责“能恢复就恢复”。
        String token = bearerToken(request);
        if (token == null) {
            return true;
        }

        // 2. Redis 中不存在该 key，说明 Token 已过期或无效，继续交给后续登录拦截器判断。
        String key = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        if (entries.isEmpty()) {
            return true;
        }

        // 3. 将轻量登录信息放入当前线程，后续 Controller/Service 无需重复查询 Redis。
        userContext.set(new LoginUser(
                Long.valueOf(value(entries, "id")),
                null,
                null,
                value(entries, "city"),
                null,
                doubleValue(entries, "lng"),
                doubleValue(entries, "lat"),
                null));
        // 4. 用户持续访问就续期；随机抖动避免大量 Token 在同一秒过期。
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
        // Tomcat 会复用工作线程，不 remove 会产生严重的串号风险。
        userContext.clear();
    }
}

