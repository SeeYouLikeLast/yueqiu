package com.hm.badminton.utils;

import com.hm.badminton.config.AmapProperties;
import com.hm.badminton.dto.LoginUser;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * 统一解析读接口使用的城市上下文。
 *
 * <p>场所顺序等演示数据需要城市参与定位，但不应把中文城市反复拼进 URL。
 * 前端将当前定位城市写入 {@code X-Location-City}，登录用户没有传该请求头时
 * 才回退到 Token 中的已保存城市，最后使用系统默认城市。</p>
 */
@Component
public class LocationContextResolver {

    private final UserContext userContext;
    private final AmapProperties amapProperties;

    public LocationContextResolver(UserContext userContext, AmapProperties amapProperties) {
        this.userContext = userContext;
        this.amapProperties = amapProperties;
    }

    public String resolveCity(String requestedCity) {
        if (hasText(requestedCity)) {
            return URLDecoder.decode(requestedCity.trim(), StandardCharsets.UTF_8);
        }
        LoginUser user = userContext.current().orElse(null);
        if (user != null && hasText(user.getCity())) {
            return user.getCity().trim();
        }
        return amapProperties.getDefaultCity();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
