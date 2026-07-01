package com.hm.badminton.controller;

import com.hm.badminton.common.ApiResponse;
import com.hm.badminton.dto.BlogView;
import com.hm.badminton.service.IFollowService;
import com.hm.badminton.utils.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/follows")
public class FollowController {

    private final IFollowService followService;
    private final UserContext userContext;

    public FollowController(IFollowService followService, UserContext userContext) {
        this.followService = followService;
        this.userContext = userContext;
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Boolean>> isFollowed(@PathVariable Long id) {
        return ApiResponse.ok(Map.of("followed", followService.isFollowed(userContext.requireUserId(), id)));
    }

    @PutMapping("/{id}/{follow}")
    public ApiResponse<Void> follow(@PathVariable Long id, @PathVariable boolean follow) {
        followService.follow(userContext.requireUserId(), id, follow);
        return ApiResponse.ok();
    }

    @GetMapping("/common/{id}")
    public ApiResponse<List<BlogView>> common(@PathVariable Long id) {
        return ApiResponse.ok(followService.commonFollow(userContext.requireUserId(), id));
    }
}
