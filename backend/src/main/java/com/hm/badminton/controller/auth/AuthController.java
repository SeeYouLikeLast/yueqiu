package com.hm.badminton.controller.auth;

import com.hm.badminton.common.ApiResponse;
import com.hm.badminton.dto.UserPublicProfile;
import com.hm.badminton.dto.auth.CodeRequest;
import com.hm.badminton.dto.auth.CodeResponse;
import com.hm.badminton.dto.auth.LocationRequest;
import com.hm.badminton.dto.auth.LoginRequest;
import com.hm.badminton.dto.auth.LoginResponse;
import com.hm.badminton.dto.auth.ProfileUpdateRequest;
import com.hm.badminton.dto.auth.RegisterRequest;
import com.hm.badminton.service.auth.IAuthService;
import com.hm.badminton.utils.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    // 构造器注入（官方推荐，依赖关系清晰）    
    private final IAuthService authService;
    private final UserContext userContext;

    public AuthController(IAuthService authService, UserContext userContext) {
        this.authService = authService;
        this.userContext = userContext;
    }

    @PostMapping("/register")
    public ApiResponse<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/code")
    public ApiResponse<CodeResponse> sendCode(@Valid @RequestBody CodeRequest request) {
        return ApiResponse.ok(authService.sendCode(request));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me() {
        return ApiResponse.ok(authService.me(userContext.require()));
    }

    @PutMapping("/me")
    public ApiResponse<Map<String, Object>> updateMe(@RequestBody ProfileUpdateRequest request) {
        return ApiResponse.ok(authService.updateMe(userContext.requireUserId(), request));
    }

    @GetMapping("/users/{id}")
    public ApiResponse<UserPublicProfile> publicProfile(@PathVariable Long id) {
        return ApiResponse.ok(authService.publicProfile(id, userContext.current().orElse(null)));
    }

    @PutMapping("/location")
    public ApiResponse<Map<String, Object>> updateLocation(@Valid @RequestBody LocationRequest body,
                                                           HttpServletRequest request) {
        return ApiResponse.ok(authService.updateLocation(bearerToken(request), userContext.requireUserId(), body));
    }

    private String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
