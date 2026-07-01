package com.hm.badminton.service;

import com.hm.badminton.common.PageResult;
import com.hm.badminton.entity.PlayerProfile;
import com.hm.badminton.entity.SportActivity;
import com.hm.badminton.service.impl.SocialService;

import java.util.List;
import java.util.Map;

public interface ISocialService {
    PageResult<PlayerProfile> players(String sportCode,
                                      String city,
                                      String area,
                                      String level,
                                      Double lng,
                                      Double lat,
                                      int page,
                                      int size);

    PlayerProfile me(Long userId);

    void saveProfile(Long userId, SocialService.ProfileRequest request);

    PageResult<SportActivity> activities(String sportCode, String city, String level, int page, int size);

    Long createActivity(Long userId, SocialService.ActivityRequest request);

    void join(Long userId, Long activityId);

    void cancel(Long userId, Long activityId);

    SportActivity detail(Long id);

    List<Map<String, Object>> members(Long activityId);
}
