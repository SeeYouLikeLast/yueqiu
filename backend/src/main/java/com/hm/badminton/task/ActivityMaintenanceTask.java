package com.hm.badminton.task;

import com.hm.badminton.service.social.IActivityMaintenanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
public class ActivityMaintenanceTask {

    private static final Logger log = LoggerFactory.getLogger(ActivityMaintenanceTask.class);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final IActivityMaintenanceService activityMaintenanceService;

    public ActivityMaintenanceTask(IActivityMaintenanceService activityMaintenanceService) {
        this.activityMaintenanceService = activityMaintenanceService;
    }

    /**
     * 启动时补执行一次，防止应用在凌晨任务执行时处于停机状态。
     * 活动维护只对齐 DEMO_* 演示活动到当天，真实用户活动不会被改期。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void refreshAfterStartup() {
        refresh("startup");
    }

    /** 每天 00:05 将演示活动对齐到当天、滚动演示库存，并关闭已结束的真实活动。 */
    @Scheduled(cron = "0 5 0 * * ?", zone = "Asia/Shanghai")
    public void refreshEveryDay() {
        refresh("daily");
    }

    private void refresh(String trigger) {
        try {
            IActivityMaintenanceService.ActivityRefreshResult result =
                    activityMaintenanceService.refreshActivityTimes(LocalDateTime.now(BUSINESS_ZONE));
            if (result.getRefreshedDemoActivities() > 0
                    || result.getFinishedUserActivities() > 0
                    || result.getRefreshedDemoInventories() > 0) {
                log.info("demo maintenance finished, trigger={}, activitiesRefreshed={}, inventoriesRefreshed={}, userActivitiesFinished={}",
                        trigger,
                        result.getRefreshedDemoActivities(),
                        result.getRefreshedDemoInventories(),
                        result.getFinishedUserActivities());
            }
        } catch (RuntimeException e) {
            // 维护失败不阻断应用启动；下一次定时任务会再次尝试。
            log.warn("activity maintenance failed, trigger={}", trigger, e);
        }
    }
}
