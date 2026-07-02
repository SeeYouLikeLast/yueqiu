package com.hm.badminton.controller;

import com.hm.badminton.utils.UserContext;
import com.hm.badminton.common.ApiResponse;
import com.hm.badminton.common.PageResult;
import com.hm.badminton.dto.LoginUser;
import com.hm.badminton.entity.PlayerProfile;
import com.hm.badminton.entity.SportActivity;
import com.hm.badminton.service.ISocialService;
import com.hm.badminton.service.impl.SocialService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/social")
public class SocialController {

    private final ISocialService socialService;
    private final UserContext userContext;

    public SocialController(ISocialService socialService, UserContext userContext) {
        this.socialService = socialService;
        this.userContext = userContext;
    }

    @GetMapping("/players")
    public ApiResponse<PageResult<PlayerProfile>> players(@RequestParam(required = false) String sport,
                                                          @RequestParam(required = false) String city,
                                                          @RequestParam(required = false) String area,
                                                          @RequestParam(required = false) String level,
                                                          @RequestParam(required = false) Double lng,
                                                          @RequestParam(required = false) Double lat,
                                                          @RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "12") int size) {
        LoginUser user = userContext.current().orElse(null);
        return ApiResponse.ok(socialService.players(
                sport,
                firstText(city, user == null ? null : user.getCity()),
                area,
                level,
                lng == null && user != null ? user.getLongitude() : lng,
                lat == null && user != null ? user.getLatitude() : lat,
                page,
                size));
    }

    @GetMapping("/profile/me")
    public ApiResponse<PlayerProfile> myProfile() {
        return ApiResponse.ok(socialService.me(userContext.requireUserId()));
    }

    @PostMapping("/profile/me")
    public ApiResponse<Void> saveProfile(@Valid @RequestBody SocialService.ProfileRequest request) {
        socialService.saveProfile(userContext.requireUserId(), request);
        return ApiResponse.ok();
    }

    @GetMapping("/activities")
    public ApiResponse<PageResult<SportActivity>> activities(@RequestParam(required = false) String sport,
                                                             @RequestParam(required = false) String city,
                                                             @RequestParam(required = false) String level,
                                                             @RequestParam(defaultValue = "1") int page,
                                                             @RequestParam(defaultValue = "12") int size) {
        LoginUser user = userContext.current().orElse(null);
        return ApiResponse.ok(socialService.activities(sport, firstText(city, user == null ? null : user.getCity()), level, page, size));
    }

    @GetMapping("/activities/{id}")
    public ApiResponse<SportActivity> detail(@PathVariable Long id) {
        return ApiResponse.ok(socialService.detail(id));
    }

    @GetMapping("/activities/{id}/members")
    public ApiResponse<List<Map<String, Object>>> members(@PathVariable Long id) {
        return ApiResponse.ok(socialService.members(id));
    }

    @PostMapping("/activities")
    public ApiResponse<Map<String, Long>> create(@Valid @RequestBody SocialService.ActivityRequest request) {
        return ApiResponse.ok(Map.of("activityId", socialService.createActivity(userContext.requireUserId(), request)));
    }

    @PostMapping("/activities/{id}/join")
    public ApiResponse<Void> join(@PathVariable Long id) {
        socialService.join(userContext.requireUserId(), id);
        return ApiResponse.ok();
    }

    @PostMapping("/activities/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable Long id) {
        socialService.cancel(userContext.requireUserId(), id);
        return ApiResponse.ok();
    }

    private String firstText(String value, String fallback) {
        if (value != null && !value.isBlank()) {
            return value;
        }
        return fallback;
    }
}

