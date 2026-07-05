package com.hm.badminton.service.auth;

import com.hm.badminton.dto.LoginUser;
import com.hm.badminton.dto.UserPublicProfile;
import com.hm.badminton.dto.auth.CodeRequest;
import com.hm.badminton.dto.auth.CodeResponse;
import com.hm.badminton.dto.auth.LocationRequest;
import com.hm.badminton.dto.auth.LoginRequest;
import com.hm.badminton.dto.auth.LoginResponse;
import com.hm.badminton.dto.auth.ProfileUpdateRequest;
import com.hm.badminton.dto.auth.RegisterRequest;

import java.util.Map;

public interface IAuthService {
    CodeResponse sendCode(CodeRequest request);

    LoginResponse register(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    Map<String, Object> me(LoginUser user);

    Map<String, Object> updateMe(Long userId, ProfileUpdateRequest request);

    UserPublicProfile publicProfile(Long userId, LoginUser currentUser);

    void updateLocation(String token, Long userId, LocationRequest request);
}


