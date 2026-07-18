package com.hm.badminton.config;

import com.hm.badminton.interceptor.LoginInterceptor;
import com.hm.badminton.interceptor.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 的统一入口配置。
 *
 * <p>认证顺序必须保持为：先由 {@link RefreshTokenInterceptor} 尝试恢复用户，再由
 * {@link LoginInterceptor} 拦截需要登录的路径。顺序反过来时，登录拦截器将永远看不到用户。</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RefreshTokenInterceptor refreshTokenInterceptor;
    private final LoginInterceptor loginInterceptor;
    private final CorsProperties corsProperties;

    public WebConfig(RefreshTokenInterceptor refreshTokenInterceptor,
                     LoginInterceptor loginInterceptor,
                     CorsProperties corsProperties) {
        this.refreshTokenInterceptor = refreshTokenInterceptor;
        this.loginInterceptor = loginInterceptor;
        this.corsProperties = corsProperties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 第一层拦截所有请求：有 Token 就刷新登录态，没有 Token 也允许继续访问公开接口。
        registry.addInterceptor(refreshTokenInterceptor)
                .addPathPatterns("/**")
                .order(0);
        // 第二层只拦截需要身份的接口。用户信息已由上一层放入 UserContext。
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns(
                        "/auth/me",
                        "/auth/location",
                        "/blogs/of/follow",
                        "/cart/**",
                        "/orders/**",
                        "/payments/**",
                        "/follows/**",
                        "/seckill/*/*",
                        "/social/profile/me",
                        "/social/activities/*/join")
                .order(1);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 开发时前后端端口不同；生产通常由 Nginx 同源转发，但仍保留受控跨域配置。
        registry.addMapping("/**")
                .allowedOrigins(corsProperties.getAllowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}


