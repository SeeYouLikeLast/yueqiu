package com.hm.badminton.controller;

import com.hm.badminton.utils.UserContext;
import com.hm.badminton.common.ApiResponse;
import com.hm.badminton.entity.SeckillActivity;
import com.hm.badminton.entity.SeckillOrder;
import com.hm.badminton.service.ISeckillService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/seckill")
public class SeckillController {

    private final ISeckillService seckillService;
    private final UserContext userContext;

    public SeckillController(ISeckillService seckillService, UserContext userContext) {
        this.seckillService = seckillService;
        this.userContext = userContext;
    }

    @GetMapping("/activities")
    public ApiResponse<List<SeckillActivity>> activities(@RequestParam(required = false) String sport,
                                                         @RequestParam(required = false) Long categoryId) {
        return ApiResponse.ok(seckillService.list(sport, categoryId));
    }

    @PostMapping("/activities/{activityId}/orders")
    public ApiResponse<Map<String, Long>> submit(@PathVariable Long activityId) {
        return ApiResponse.ok(Map.of("orderId", seckillService.submit(userContext.requireUserId(), activityId)));
    }

    @GetMapping("/orders/{orderId}")
    public ApiResponse<SeckillOrder> order(@PathVariable Long orderId) {
        return ApiResponse.ok(seckillService.order(userContext.requireUserId(), orderId));
    }

    @GetMapping("/orders")
    public ApiResponse<List<SeckillOrder>> myOrders() {
        return ApiResponse.ok(seckillService.myOrders(userContext.requireUserId()));
    }

    @PostMapping("/preload")
    public ApiResponse<Map<String, Object>> preload() {
        userContext.require();
        return ApiResponse.ok(seckillService.preload());
    }
}
