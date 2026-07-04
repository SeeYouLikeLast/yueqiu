package com.hm.badminton.service.community.impl;

import com.hm.badminton.mapper.community.BlogMapper;
import com.hm.badminton.service.community.IBlogMaintenanceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BlogMaintenanceService implements IBlogMaintenanceService {

    private final BlogMapper blogMapper;

    public BlogMaintenanceService(BlogMapper blogMapper) {
        this.blogMapper = blogMapper;
    }

    @Override
    @Transactional
    public int archiveDeletedBlogs(int retentionDays, int batchSize) {
        blogMapper.archiveDeletedBlogs(retentionDays, batchSize);
        return blogMapper.markDeletedBlogsArchived(retentionDays, batchSize);
    }

    @Override
    @Transactional
    public int cleanupArchivedDeletedBlogs(int retentionDays, int batchSize) {
        return blogMapper.deleteExpiredDeletedBlogs(retentionDays, batchSize);
    }
}
