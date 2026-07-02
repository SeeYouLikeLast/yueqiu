package com.hm.badminton.utils;

import com.hm.badminton.common.BusinessException;
import com.hm.badminton.dto.LoginUser;
import org.springframework.stereotype.Component;

import java.util.Optional;

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

