package com.hm.badminton.config;

import com.hm.badminton.interceptor.LoginInterceptor;
import com.hm.badminton.interceptor.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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
        registry.addInterceptor(refreshTokenInterceptor)
                .addPathPatterns("/**")
                .order(0);
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns(
                        "/admin/**",
                        "/auth/me",
                        "/auth/location",
                        "/blogs/of/follow",
                        "/equipment/cart/**",
                        "/equipment/orders/**",
                        "/follows/**",
                        "/seckill/activities/*/orders",
                        "/seckill/orders/**",
                        "/seckill/preload",
                        "/social/profile/me",
                        "/social/activities/*/join",
                        "/social/activities/*/cancel",
                        "/venue/orders/**",
                        "/venue-products/*/quick-pay",
                        "/venues/reviews",
                        "/venues/*/favorite",
                        "/venues/favorites/me")
                .order(1);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(corsProperties.getAllowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}

