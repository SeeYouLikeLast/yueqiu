package com.hm.badminton.interceptor;

import com.hm.badminton.utils.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录校验拦截器，仅配置在受保护路径上。
 *
 * <p>它不解析 Token，只检查 {@link RefreshTokenInterceptor} 是否已将用户放入
 * {@link UserContext}。职责拆开后，公开接口也能识别“可选登录用户”，受保护接口则统一返回 401。</p>
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    private final UserContext userContext;

    public LoginInterceptor(UserContext userContext) {
        this.userContext = userContext;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 浏览器跨域预检不带业务 Token，必须直接放行。
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || userContext.current().isPresent()) {
            return true;
        }

        // 返回与全局 ApiResponse 相同的 JSON 结构，前端收到 401 后跳转登录页。
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"请先登录\",\"data\":null}");
        return false;
    }
}
