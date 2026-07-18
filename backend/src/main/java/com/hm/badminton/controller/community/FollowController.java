package com.hm.badminton.controller.community;

import com.hm.badminton.common.ApiResponse;
import com.hm.badminton.service.community.IFollowService;
import com.hm.badminton.utils.UserContext;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 关注、取消关注和关注状态查询入口。 */
@RestController
@RequestMapping("/follows")
public class FollowController {

    private final IFollowService followService;
    private final UserContext userContext;

    public FollowController(IFollowService followService, UserContext userContext) {
        this.followService = followService;
        this.userContext = userContext;
    }

    @PutMapping("/{id}/{follow}")
    public ApiResponse<Void> follow(@PathVariable Long id, @PathVariable boolean follow) {
        followService.follow(userContext.requireUserId(), id, follow);
        return ApiResponse.ok();
    }
}


