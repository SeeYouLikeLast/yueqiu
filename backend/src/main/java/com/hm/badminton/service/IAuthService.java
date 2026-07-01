package com.hm.badminton.service;

import com.hm.badminton.dto.LoginUser;
import com.hm.badminton.dto.UserPublicProfile;
import com.hm.badminton.service.impl.AuthService;

import java.util.Map;

public interface IAuthService {
    AuthService.CodeResponse sendCode(AuthService.CodeRequest request);

    AuthService.LoginResponse register(AuthService.RegisterRequest request);

    AuthService.LoginResponse login(AuthService.LoginRequest request);

    Map<String, Object> me(LoginUser user);

    UserPublicProfile publicProfile(Long userId, LoginUser currentUser);

    void updateLocation(String token, Long userId, AuthService.LocationRequest request);
}
