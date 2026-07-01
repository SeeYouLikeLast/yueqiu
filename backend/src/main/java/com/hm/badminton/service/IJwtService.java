package com.hm.badminton.service;

import com.hm.badminton.dto.LoginUser;
import com.hm.badminton.service.impl.JwtService;

import java.util.Optional;

public interface IJwtService {
    String createToken(LoginUser user);

    Optional<JwtService.TokenPayload> parse(String token);
}
