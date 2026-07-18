package com.hm.badminton.service.social;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

public interface IActivityMaintenanceService {

    ActivityRefreshResult refreshActivityTimes(LocalDateTime currentTime);

    @Data
    @AllArgsConstructor
    class ActivityRefreshResult {
        private int refreshedDemoActivities;
        private int finishedUserActivities;
        private int refreshedDemoInventories;
    }
}
