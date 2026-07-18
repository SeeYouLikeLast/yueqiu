package com.hm.badminton.utils;

import com.hm.badminton.common.BusinessException;
import com.hm.badminton.dto.LoginUser;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 当前请求的轻量用户上下文，作用类似 HMDP 的 {@code UserHolder}。
 *
 * <p>数据只在一次 HTTP 请求所在线程内有效。它用于减少 Controller 与 Service 之间反复传递
 * 完整用户对象，但不能替代数据库，也不能跨线程或跨请求保存用户状态。</p>
 */
@Component
public class UserContext {

    private static final ThreadLocal<LoginUser> CURRENT = new ThreadLocal<>();

    public void set(LoginUser user) {
        CURRENT.set(user);
    }

    public Optional<LoginUser> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public LoginUser require() {
        return current().orElseThrow(() -> new BusinessException(401, "请先登录"));
    }

    public Long requireUserId() {
        return require().getId();
    }

    public void clear() {
        CURRENT.remove();
    }
}

