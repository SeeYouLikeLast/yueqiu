package com.hm.badminton.service.community;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hm.badminton.entity.Follow;

public interface IFollowService extends IService<Follow> {

    boolean isFollowed(Long userId, Long followUserId);

    void follow(Long userId, Long followUserId, boolean follow);
}


