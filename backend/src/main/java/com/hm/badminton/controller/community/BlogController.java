package com.hm.badminton.controller.community;

import com.hm.badminton.common.ApiResponse;
import com.hm.badminton.common.PageResult;
import com.hm.badminton.dto.BlogView;
import com.hm.badminton.dto.ScrollResult;
import com.hm.badminton.service.community.IBlogService;
import com.hm.badminton.service.community.impl.BlogService;
import com.hm.badminton.utils.UserContext;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/blogs")
public class BlogController {

    private final IBlogService blogService;
    private final UserContext userContext;

    public BlogController(IBlogService blogService, UserContext userContext) {
        this.blogService = blogService;
        this.userContext = userContext;
    }

    @GetMapping
    public ApiResponse<PageResult<BlogView>> list(@RequestParam(defaultValue = "recommend") String channel,
                                                  @RequestParam(required = false) String sport,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(blogService.listBlogs(channel, sport, keyword, page, size, userContext.current().orElse(null)));
    }

    @GetMapping("/of/user/{userId}")
    public ApiResponse<PageResult<BlogView>> userBlogs(@PathVariable Long userId,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(blogService.listUserBlogs(userId, page, size, userContext.current().orElse(null)));
    }

    @GetMapping("/of/follow")
    public ApiResponse<ScrollResult<BlogView>> followFeed(@RequestParam(required = false) Long lastId,
                                                          @RequestParam(defaultValue = "0") Integer offset) {
        return ApiResponse.ok(blogService.followFeed(lastId, offset, userContext.require()));
    }

    @GetMapping("/{id}")
    public ApiResponse<BlogView> detail(@PathVariable Long id) {
        return ApiResponse.ok(blogService.detail(id, userContext.current().orElse(null)));
    }

    @PostMapping
    public ApiResponse<Map<String, Long>> publish(@Valid @RequestBody BlogService.BlogCreateRequest request) {
        return ApiResponse.ok(Map.of("blogId", blogService.publish(userContext.requireUserId(), request)));
    }

    @PutMapping("/{id}/like")
    public ApiResponse<Void> like(@PathVariable Long id) {
        blogService.like(userContext.requireUserId(), id);
        return ApiResponse.ok();
    }
}


