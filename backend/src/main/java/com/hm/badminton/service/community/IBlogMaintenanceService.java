package com.hm.badminton.service.community;

public interface IBlogMaintenanceService {

    int archiveDeletedBlogs(int retentionDays, int batchSize);

    int cleanupArchivedDeletedBlogs(int retentionDays, int batchSize);
}
