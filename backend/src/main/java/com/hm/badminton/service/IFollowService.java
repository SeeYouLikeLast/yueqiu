package com.hm.badminton.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hm.badminton.dto.BlogView;
import com.hm.badminton.entity.Follow;

import java.util.List;

public interface IFollowService extends IService<Follow> {

    boolean isFollowed(Long userId, Long followUserId);

    void follow(Long userId, Long followUserId, boolean follow);

    List<BlogView> commonFollow(Long userId, Long targetUserId);
}
