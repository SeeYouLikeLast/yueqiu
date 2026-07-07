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
import java.util.Comparator;
import java.util.Collections;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    /**
     * 关注流滚动分页（推拉结合模式）。
     * 普通作者走推模式（发布时写入粉丝 inbox），大 V 走拉模式（读取大 V 的 outbox），
     * 最后合并、去重、排序、窗口截取，返回统一时间线。
     *
     * @param maxTime 上一页最小 score（游标），首次传 null
     * @param offset  与 maxTime 同 score 的已拉取条数（防重复）
     */
    @Override
    public ScrollResult<BlogView> followFeed(Long maxTime, Integer offset, LoginUser currentUser) {
        // 1. 未登录用户无关注流
        if (currentUser == null) {
            return new ScrollResult<>(Collections.emptyList(), 0L, 0);
        }
        long max = maxTime == null ? Long.MAX_VALUE : maxTime;
        int safeOffset = offset == null ? 0 : Math.max(offset, 0);

        // 2. 推模式：读取用户收件箱（普通作者发布时已写入）
        List<FeedEntry> merged = new ArrayList<>();
        merged.addAll(readFeedEntries(RedisConstants.FEED_KEY + currentUser.getId(), max));
        // 3. 拉模式：遍历关注的大 V，读取其 outbox（outbox 空时降级 DB）
        merged.addAll(readBigVOutboxEntries(currentUser.getId(), max));
        merged.addAll(readFollowedBlogsFromDatabase(currentUser.getId(), max));
        // 4. 合并去重 + 排序：同一 blogId 保留 score 最大的（防止推拉重复），
        //    然后按 score 倒序、blogId 倒序排列
        List<FeedEntry> sorted = merged.stream()
                .collect(Collectors.toMap(FeedEntry::blogId, Function.identity(),
                        (a, b) -> a.score() >= b.score() ? a : b))
                .values()
                .stream()
                .sorted(Comparator.comparingLong(FeedEntry::score).reversed()
                        .thenComparing(FeedEntry::blogId, Comparator.reverseOrder()))
                .toList();

        // 5. 滚动游标偏移：跳过上一页末尾与 maxTime 同 score 的已返回条目
        List<FeedEntry> window = new ArrayList<>(DEFAULT_SIZE);
        int skippedSameScore = 0;
        for (FeedEntry entry : sorted) {
            if (maxTime != null && entry.score() == max && skippedSameScore < safeOffset) {
                skippedSameScore++;
                continue;
            }
            window.add(entry);
            if (window.size() >= DEFAULT_SIZE) {
                break;
            }
        }

        // 6. 窗口为空时降级到数据库直接查关注用户的博客
        if (window.isEmpty()) {
            PageResult<BlogView> fallback = listFollowBlogs(1, DEFAULT_SIZE, currentUser);
            return new ScrollResult<>(fallback.getRecords(), 0L, 0);
        }
        // 7. 批量加载博客实体，保留窗口顺序
        List<Blog> blogs = loadBlogsByFeedEntries(window);
        if (blogs.isEmpty()) {
            return new ScrollResult<>(Collections.emptyList(), 0L, 0);
        }
        // 8. 计算下一页游标：minTime = 本窗口最小 score，sameCount = 同 score 条目数
        long minTime = window.stream().mapToLong(FeedEntry::score).min().orElse(0L);
        int sameCount = (int) window.stream().filter(entry -> entry.score() == minTime).count();
        // 9. 填充用户视角数据（头像、昵称、点赞/关注状态）返回
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

        long timestamp = System.currentTimeMillis();
        redisTemplate.opsForZSet().add(RedisConstants.BLOG_OUTBOX_KEY + userId, String.valueOf(blog.getId()), timestamp);
        if (!isBigV(userId)) {
            List<Follow> followers = followMapper.selectList(new LambdaQueryWrapper<Follow>().eq(Follow::getFollowUserId, userId));
            for (Follow follower : followers) {
                redisTemplate.opsForZSet().add(RedisConstants.FEED_KEY + follower.getUserId(), String.valueOf(blog.getId()), timestamp);
            }
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
        redisTemplate.opsForZSet().remove(RedisConstants.BLOG_OUTBOX_KEY + userId, String.valueOf(blogId));
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

    private List<FeedEntry> readFeedEntries(String key, long maxScore) {
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, maxScore, 0, DEFAULT_SIZE * 3);
        if (tuples == null || tuples.isEmpty()) {
            return Collections.emptyList();
        }
        return tuples.stream()
                .map(this::toFeedEntry)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<FeedEntry> readBigVOutboxEntries(Long userId, long maxScore) {
        List<Long> bigVIds = followedBigVIds(userId);
        if (bigVIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<FeedEntry> entries = new ArrayList<>();
        for (Long bigVId : bigVIds) {
            List<FeedEntry> outboxEntries = readFeedEntries(RedisConstants.BLOG_OUTBOX_KEY + bigVId, maxScore);
            entries.addAll(outboxEntries.isEmpty() ? readBigVBlogsFromDatabase(bigVId, maxScore) : outboxEntries);
        }
        return entries;
    }

    private List<Long> followedBigVIds(Long userId) {
        List<Long> followUserIds = followedUserIds(userId);
        if (followUserIds.isEmpty()) {
            return Collections.emptyList();
        }
        return userMapper.selectByIds(followUserIds)
                .stream()
                .filter(user -> Integer.valueOf(1).equals(user.getStatus()))
                .filter(user -> Integer.valueOf(1).equals(user.getIsBigV()))
                .map(UserAccount::getId)
                .toList();
    }

    private List<Long> followedUserIds(Long userId) {
        return followMapper.selectList(new LambdaQueryWrapper<Follow>().eq(Follow::getUserId, userId))
                .stream()
                .map(Follow::getFollowUserId)
                .toList();
    }

    private List<Blog> loadBlogsByFeedEntries(List<FeedEntry> entries) {
        List<Long> ids = entries.stream().map(FeedEntry::blogId).toList();
        Map<Long, Blog> blogMap = listByIds(ids).stream()
                .filter(blog -> Integer.valueOf(1).equals(blog.getStatus()))
                .collect(Collectors.toMap(Blog::getId, Function.identity()));
        return ids.stream().map(blogMap::get).filter(Objects::nonNull).toList();
    }

    private List<FeedEntry> readBigVBlogsFromDatabase(Long bigVId, long maxScore) {
        return list(new LambdaQueryWrapper<Blog>()
                .eq(Blog::getStatus, 1)
                .eq(Blog::getUserId, bigVId)
                .orderByDesc(Blog::getCreatedAt)
                .last("limit " + DEFAULT_SIZE))
                .stream()
                .map(blog -> new FeedEntry(blog.getId(), blog.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()))
                .filter(entry -> entry.score() <= maxScore)
                .toList();
    }

    private List<FeedEntry> readFollowedBlogsFromDatabase(Long userId, long maxScore) {
        List<Long> followUserIds = followedUserIds(userId);
        if (followUserIds.isEmpty()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<Blog> wrapper = new LambdaQueryWrapper<Blog>()
                .eq(Blog::getStatus, 1)
                .in(Blog::getUserId, followUserIds)
                .orderByDesc(Blog::getCreatedAt)
                .orderByDesc(Blog::getId)
                .last("limit " + DEFAULT_SIZE * 3);
        if (maxScore < Long.MAX_VALUE) {
            LocalDateTime maxCreatedAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(maxScore), ZoneId.systemDefault());
            wrapper.le(Blog::getCreatedAt, maxCreatedAt);
        }
        return list(wrapper)
                .stream()
                .map(blog -> new FeedEntry(blog.getId(), blog.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()))
                .filter(entry -> entry.score() <= maxScore)
                .toList();
    }

    private FeedEntry toFeedEntry(ZSetOperations.TypedTuple<String> tuple) {
        if (tuple == null || tuple.getValue() == null || tuple.getScore() == null) {
            return null;
        }
        try {
            return new FeedEntry(Long.valueOf(tuple.getValue()), tuple.getScore().longValue());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isBigV(Long userId) {
        UserAccount user = userMapper.selectById(userId);
        return user != null && Integer.valueOf(1).equals(user.getIsBigV());
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

    private record FeedEntry(Long blogId, long score) {
    }

}


