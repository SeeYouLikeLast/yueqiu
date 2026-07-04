package com.hm.badminton.service.community.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hm.badminton.common.BusinessException;
import com.hm.badminton.common.PageResult;
import com.hm.badminton.dto.BlogView;
import com.hm.badminton.dto.LoginUser;
import com.hm.badminton.dto.ScrollResult;
import com.hm.badminton.dto.community.BlogCreateRequest;
import com.hm.badminton.entity.Blog;
import com.hm.badminton.entity.Follow;
import com.hm.badminton.entity.UserAccount;
import com.hm.badminton.mapper.community.BlogMapper;
import com.hm.badminton.mapper.community.FollowMapper;
import com.hm.badminton.mapper.auth.UserMapper;
import com.hm.badminton.service.community.IBlogService;
import com.hm.badminton.service.community.IFollowService;
import com.hm.badminton.constants.RedisConstants;
import com.hm.badminton.utils.CacheClient;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.time.LocalDateTime;

@Service
public class BlogService extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    private static final int DEFAULT_SIZE = 10;

    private final StringRedisTemplate redisTemplate;
    private final UserMapper userMapper;
    private final FollowMapper followMapper;
    private final IFollowService followService;
    private final CacheClient cacheClient;

    public BlogService(StringRedisTemplate redisTemplate,
                       UserMapper userMapper,
                       FollowMapper followMapper,
                       IFollowService followService,
                       CacheClient cacheClient) {
        this.redisTemplate = redisTemplate;
        this.userMapper = userMapper;
        this.followMapper = followMapper;
        this.followService = followService;
        this.cacheClient = cacheClient;
    }

    @Override
    public PageResult<BlogView> listBlogs(String channel, String sportCode, String keyword, int page, int size, LoginUser currentUser) {
        if ("follow".equalsIgnoreCase(channel)) {
            return listFollowBlogs(page, size, currentUser);
        }
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 30);
        LambdaQueryWrapper<Blog> wrapper = baseWrapper(sportCode, keyword)
                .orderByDesc(Blog::getLiked)
                .orderByDesc(Blog::getCreatedAt);
        Page<Blog> result = page(new Page<>(safePage, safeSize), wrapper);
        return new PageResult<>(enrich(result.getRecords(), currentUser), result.getTotal(), safePage, safeSize);
    }

    @Override
    public PageResult<BlogView> listUserBlogs(Long userId, int page, int size, LoginUser currentUser) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 30);
        Page<Blog> result = page(new Page<>(safePage, safeSize), new LambdaQueryWrapper<Blog>()
                .eq(Blog::getStatus, 1)
                .eq(Blog::getUserId, userId)
                .orderByDesc(Blog::getCreatedAt));
        return new PageResult<>(enrich(result.getRecords(), currentUser), result.getTotal(), safePage, safeSize);
    }

    @Override
    public ScrollResult<BlogView> followFeed(Long maxTime, Integer offset, LoginUser currentUser) {
        // 1. 未登录用户无关注流，直接返回空
        if (currentUser == null) {
            return new ScrollResult<>(Collections.emptyList(), 0L, 0);
        }
        // 2. 规范化分页参数：maxTime 为上一页最小 score（游标），offset 为同 score 已拉取条数
        long max = maxTime == null ? Long.MAX_VALUE : maxTime;
        int safeOffset = offset == null ? 0 : Math.max(offset, 0);
        // 3. 从 Redis ZSet 按 score 倒序拉取 feed 中的博客 ID
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(RedisConstants.FEED_KEY + currentUser.getId(), 0, max, safeOffset, DEFAULT_SIZE);
        // 4. feed 为空时降级到数据库直接查关注用户的博客
        if (tuples == null || tuples.isEmpty()) {
            PageResult<BlogView> fallback = listFollowBlogs(1, DEFAULT_SIZE, currentUser);
            return new ScrollResult<>(fallback.getRecords(), 0L, 0);
        }
        // 5. 从 ZSet 结果中提取博客 ID 列表
        List<Long> ids = tuples.stream()
                .map(ZSetOperations.TypedTuple::getValue)
                .filter(Objects::nonNull)
                .map(Long::valueOf)
                .toList();
        // 6. 批量查询博客并过滤已删除/下架的（status != 1），保留 ZSet 原始顺序
        Map<Long, Blog> blogMap = listByIds(ids).stream()
                .filter(blog -> Integer.valueOf(1).equals(blog.getStatus()))
                .collect(Collectors.toMap(Blog::getId, Function.identity()));
        List<Blog> blogs = ids.stream().map(blogMap::get).filter(Objects::nonNull).toList();
        // 7. 计算下一页游标：minTime = 本批最小 score，sameCount = 与 minTime 相同 score 的条目数
        List<ZSetOperations.TypedTuple<String>> tupleList = new ArrayList<>(tuples);
        long minTime = tupleList.get(tupleList.size() - 1).getScore().longValue();
        int sameCount = 0;
        for (int i = tupleList.size() - 1; i >= 0; i--) {
            if (tupleList.get(i).getScore().longValue() == minTime) {
                sameCount++;
            } else {
                break;
            }
        }
        // 8. 填充用户视角数据（头像、昵称、点赞/关注状态）并返回
        return new ScrollResult<>(enrich(blogs, currentUser), minTime, sameCount);
    }

    @Override
    public BlogView detail(Long id, LoginUser currentUser) {
        Blog blog = cacheClient.queryWithPassThrough(
                RedisConstants.BLOG_DETAIL_KEY + id,
                Blog.class,
                () -> {
                    Blog record = getById(id);
                    return record == null || !Integer.valueOf(1).equals(record.getStatus()) ? null : record;
                },
                RedisConstants.CACHE_DETAIL_TTL,
                RedisConstants.CACHE_DETAIL_JITTER_SECONDS,
                "博客不存在");
        return enrich(List.of(blog), currentUser).get(0);
    }

    @Override
    @Transactional
    public Long publish(Long userId, BlogCreateRequest request) {
        Blog blog = new Blog();
        blog.setUserId(userId);
        blog.setSportCode(request.getSportCode());
        blog.setTitle(request.getTitle());
        blog.setContent(request.getContent());
        blog.setImageUrls(String.join(",", request.getImages() == null ? Collections.emptyList() : request.getImages()));
        blog.setRelatedType(request.getRelatedType());
        blog.setRelatedId(request.getRelatedId());
        blog.setRelatedTitle(request.getRelatedTitle());
        blog.setRelatedCoverUrl(request.getRelatedCoverUrl());
        blog.setRelatedPrice(request.getRelatedPrice());
        blog.setLiked(0);
        blog.setStatus(1);
        save(blog);

        List<Follow> followers = followMapper.selectList(new LambdaQueryWrapper<Follow>().eq(Follow::getFollowUserId, userId));
        long timestamp = System.currentTimeMillis();
        for (Follow follower : followers) {
            redisTemplate.opsForZSet().add(RedisConstants.FEED_KEY + follower.getUserId(), String.valueOf(blog.getId()), timestamp);
        }
        return blog.getId();
    }

    @Override
    @Transactional
    public void delete(Long userId, Long blogId) {
        Blog blog = getById(blogId);
        if (blog == null || !Integer.valueOf(1).equals(blog.getStatus())) {
            throw new BusinessException(404, "动态不存在");
        }
        if (!Objects.equals(blog.getUserId(), userId)) {
            throw new BusinessException(403, "只能删除自己的动态");
        }
        boolean updated = lambdaUpdate()
                .set(Blog::getStatus, 0)
                .set(Blog::getDeletedAt, LocalDateTime.now())
                .eq(Blog::getId, blogId)
                .eq(Blog::getUserId, userId)
                .eq(Blog::getStatus, 1)
                .update();
        if (!updated) {
            throw new BusinessException("删除动态失败");
        }
        cacheClient.delete(RedisConstants.BLOG_DETAIL_KEY + blogId);
        redisTemplate.delete(RedisConstants.BLOG_LIKED_KEY + blogId);
        removeFromFollowerFeeds(userId, blogId);
    }

    @Override
    @Transactional
    public void like(Long userId, Long blogId) {
        String key = RedisConstants.BLOG_LIKED_KEY + blogId;
        String member = String.valueOf(userId);
        Double score = redisTemplate.opsForZSet().score(key, member);
        if (score == null) {
            redisTemplate.opsForZSet().add(key, member, System.currentTimeMillis());
            lambdaUpdate().setSql("liked = liked + 1").eq(Blog::getId, blogId).update();
        } else {
            redisTemplate.opsForZSet().remove(key, member);
            lambdaUpdate().setSql("liked = greatest(liked - 1, 0)").eq(Blog::getId, blogId).update();
        }
        cacheClient.delete(RedisConstants.BLOG_DETAIL_KEY + blogId);
    }

    private void removeFromFollowerFeeds(Long userId, Long blogId) {
        String blogIdText = String.valueOf(blogId);
        List<Follow> followers = followMapper.selectList(new LambdaQueryWrapper<Follow>().eq(Follow::getFollowUserId, userId));
        for (Follow follower : followers) {
            redisTemplate.opsForZSet().remove(RedisConstants.FEED_KEY + follower.getUserId(), blogIdText);
        }
    }

    private PageResult<BlogView> listFollowBlogs(int page, int size, LoginUser currentUser) {
        if (currentUser == null) {
            return new PageResult<>(Collections.emptyList(), 0, page, size);
        }
        List<Long> followUserIds = followMapper.selectList(new LambdaQueryWrapper<Follow>().eq(Follow::getUserId, currentUser.getId()))
                .stream()
                .map(Follow::getFollowUserId)
                .toList();
        if (followUserIds.isEmpty()) {
            return new PageResult<>(Collections.emptyList(), 0, page, size);
        }
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 30);
        Page<Blog> result = page(new Page<>(safePage, safeSize), new LambdaQueryWrapper<Blog>()
                .eq(Blog::getStatus, 1)
                .in(Blog::getUserId, followUserIds)
                .orderByDesc(Blog::getCreatedAt));
        return new PageResult<>(enrich(result.getRecords(), currentUser), result.getTotal(), safePage, safeSize);
    }

    private LambdaQueryWrapper<Blog> baseWrapper(String sportCode, String keyword) {
        LambdaQueryWrapper<Blog> wrapper = new LambdaQueryWrapper<Blog>().eq(Blog::getStatus, 1);
        if (sportCode != null && !sportCode.isBlank() && !"all".equalsIgnoreCase(sportCode)) {
            wrapper.eq(Blog::getSportCode, sportCode.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            String text = keyword.trim();
            wrapper.and(w -> w.like(Blog::getTitle, text)
                    .or()
                    .like(Blog::getContent, text)
                    .or()
                    .like(Blog::getRelatedTitle, text));
        }
        return wrapper;
    }

    private List<BlogView> enrich(List<Blog> blogs, LoginUser currentUser) {
        if (blogs.isEmpty()) return Collections.emptyList();
        Map<Long, UserAccount> users = userMapper.selectByIds(blogs.stream().map(Blog::getUserId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(UserAccount::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        return blogs.stream()
                .map(blog -> toView(blog, users.get(blog.getUserId()), currentUser))
                .toList();
    }

    private BlogView toView(Blog blog, UserAccount author, LoginUser currentUser) {
        Long currentUserId = currentUser == null ? null : currentUser.getId();
        boolean liked = currentUserId != null && redisTemplate.opsForZSet()
                .score(RedisConstants.BLOG_LIKED_KEY + blog.getId(), String.valueOf(currentUserId)) != null;
        boolean followed = currentUserId != null && followService.isFollowed(currentUserId, blog.getUserId());
        return new BlogView(
                blog.getId(),
                blog.getUserId(),
                author == null ? "球友" : author.getNickname(),
                author == null ? null : author.getAvatar(),
                blog.getSportCode(),
                blog.getTitle(),
                blog.getContent(),
                splitImages(blog.getImageUrls()),
                blog.getRelatedType(),
                blog.getRelatedId(),
                blog.getRelatedTitle(),
                blog.getRelatedCoverUrl(),
                blog.getRelatedPrice(),
                blog.getLiked() == null ? 0 : blog.getLiked(),
                liked,
                followed,
                blog.getCreatedAt());
    }

    private List<String> splitImages(String imageUrls) {
        if (imageUrls == null || imageUrls.isBlank()) return Collections.emptyList();
        return Arrays.stream(imageUrls.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
    }

}


