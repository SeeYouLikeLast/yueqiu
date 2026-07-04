package com.hm.badminton.service.auth;

import com.hm.badminton.dto.LoginUser;
import com.hm.badminton.dto.auth.TokenPayload;

import java.util.Optional;

public interface IJwtService {
    String createToken(LoginUser user);

    Optional<TokenPayload> parse(String token);
}


