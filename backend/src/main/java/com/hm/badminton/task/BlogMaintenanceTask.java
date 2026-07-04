package com.hm.badminton.task;

import com.hm.badminton.service.community.IBlogMaintenanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BlogMaintenanceTask {

    private static final Logger log = LoggerFactory.getLogger(BlogMaintenanceTask.class);
    private static final int ARCHIVE_AFTER_DAYS = 30;
    private static final int CLEANUP_AFTER_DAYS = 90;
    private static final int BATCH_SIZE = 500;
    private static final long BATCH_PAUSE_MILLIS = 200L;

    private final IBlogMaintenanceService blogMaintenanceService;

    public BlogMaintenanceTask(IBlogMaintenanceService blogMaintenanceService) {
        this.blogMaintenanceService = blogMaintenanceService;
    }

    @Scheduled(cron = "0 0 3 * * ?", zone = "Asia/Shanghai")
    public void archiveAndCleanupDeletedBlogs() {
        int archived = runInBatches(() -> blogMaintenanceService.archiveDeletedBlogs(ARCHIVE_AFTER_DAYS, BATCH_SIZE));
        int deleted = runInBatches(() -> blogMaintenanceService.cleanupArchivedDeletedBlogs(CLEANUP_AFTER_DAYS, BATCH_SIZE));
        if (archived > 0 || deleted > 0) {
            log.info("blog maintenance finished, archived={}, deleted={}", archived, deleted);
        }
    }

    private int runInBatches(BatchAction action) {
        int total = 0;
        while (true) {
            int count = action.run();
            total += count;
            if (count < BATCH_SIZE) {
                return total;
            }
            pauseBetweenBatches();
        }
    }

    private void pauseBetweenBatches() {
        try {
            Thread.sleep(BATCH_PAUSE_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("blog maintenance interrupted", e);
        }
    }

    @FunctionalInterface
    private interface BatchAction {
        int run();
    }
}
