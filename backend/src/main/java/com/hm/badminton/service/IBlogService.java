package com.hm.badminton.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hm.badminton.common.PageResult;
import com.hm.badminton.dto.BlogView;
import com.hm.badminton.dto.LoginUser;
import com.hm.badminton.dto.ScrollResult;
import com.hm.badminton.entity.Blog;
import com.hm.badminton.service.impl.BlogService;

public interface IBlogService extends IService<Blog> {

    PageResult<BlogView> listBlogs(String channel, String sportCode, String keyword, int page, int size, LoginUser currentUser);

    PageResult<BlogView> listUserBlogs(Long userId, int page, int size, LoginUser currentUser);

    ScrollResult<BlogView> followFeed(Long maxTime, Integer offset, LoginUser currentUser);

    BlogView detail(Long id, LoginUser currentUser);

    Long publish(Long userId, BlogService.BlogCreateRequest request);

    void like(Long userId, Long blogId);
}
