package com.hm.badminton.service.auth;

import com.hm.badminton.dto.LoginUser;
import com.hm.badminton.service.auth.impl.JwtService;

import java.util.Optional;

public interface IJwtService {
    String createToken(LoginUser user);

    Optional<JwtService.TokenPayload> parse(String token);
}


