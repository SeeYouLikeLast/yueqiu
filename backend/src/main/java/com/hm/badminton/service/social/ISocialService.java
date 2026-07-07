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

    PageResult<SportActivity> activities(String sportCode, String city, String level, int page, int size);

    Long createActivity(Long userId, ActivityRequest request);

    void join(Long userId, Long activityId);
}


