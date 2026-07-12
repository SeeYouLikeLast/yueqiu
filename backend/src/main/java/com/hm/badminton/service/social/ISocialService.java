package com.hm.badminton.service.social;

import com.hm.badminton.common.PageResult;
import com.hm.badminton.dto.social.ActivityRequest;
import com.hm.badminton.dto.social.ProfileRequest;
import com.hm.badminton.entity.PlayerProfile;
import com.hm.badminton.entity.SportActivity;

public interface ISocialService {
    PageResult<PlayerProfile> players(String sportCode,
                                      String city,
                                      String area,
                                      String level,
                                      Double lng,
                                      Double lat,
                                      int page,
                                      int size,
                                      Long excludeUserId);

    PlayerProfile me(Long userId);

    void saveProfile(Long userId, ProfileRequest request);

    /**
     * 查询约球活动。scope 将活动按当前用户关系拆成三个互斥视图：
     * created（我发起）、joined（我加入）、others（尚未加入的他人活动）。
     */
    PageResult<SportActivity> activities(String sportCode,
                                         String city,
                                         String level,
                                         Long currentUserId,
                                         String scope,
                                         int page,
                                         int size);

    Long createActivity(Long userId, ActivityRequest request);

    void join(Long userId, Long activityId);
}


