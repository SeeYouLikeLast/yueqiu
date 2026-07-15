package com.hm.badminton.service.social.impl;

import com.hm.badminton.mapper.social.SocialMapper;
import com.hm.badminton.service.social.IActivityMaintenanceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ActivityMaintenanceService implements IActivityMaintenanceService {

    private final SocialMapper socialMapper;

    public ActivityMaintenanceService(SocialMapper socialMapper) {
        this.socialMapper = socialMapper;
    }

    @Override
    @Transactional
    public ActivityRefreshResult refreshActivityTimes(LocalDateTime currentTime) {
        // 先顺延演示活动，再结束真实活动，避免演示数据被误标记为已结束。
        int refreshedDemoActivities = socialMapper.refreshExpiredDemoActivities(currentTime);
        int finishedUserActivities = socialMapper.finishExpiredUserActivities(currentTime);
        return new ActivityRefreshResult(refreshedDemoActivities, finishedUserActivities);
    }
}
