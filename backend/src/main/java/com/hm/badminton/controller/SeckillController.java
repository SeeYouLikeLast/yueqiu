package com.hm.badminton.controller;

import com.hm.badminton.utils.UserContext;
import com.hm.badminton.common.ApiResponse;
import com.hm.badminton.entity.SeckillOrder;
import com.hm.badminton.service.ISeckillService;
import com.hm.badminton.vo.SeckillActivityVO;
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

    @GetMapping("/{type:[12]}")
    public ApiResponse<List<SeckillActivityVO>> typedActivities(@PathVariable Integer type,
                                                                @RequestParam(required = false) String sport,
                                                                @RequestParam(required = false) Long categoryId) {
        return ApiResponse.ok(seckillService.list(type, sport, categoryId)
                .stream()
                .map(SeckillActivityVO::from)
                .toList());
    }

    @PostMapping("/{type:[12]}/{seckillId}")
    public ApiResponse<Map<String, Long>> typedSubmit(@PathVariable Integer type, @PathVariable Long seckillId) {
        return ApiResponse.ok(Map.of("orderId", seckillService.submit(userContext.requireUserId(), type, seckillId)));
    }

    @GetMapping("/{type:[12]}/orders")
    public ApiResponse<List<SeckillOrder>> typedOrders(@PathVariable Integer type) {
        return ApiResponse.ok(seckillService.myOrders(userContext.requireUserId(), type));
    }

    @GetMapping("/all/orders")
    public ApiResponse<Map<String, List<SeckillOrder>>> allOrders() {
        Long userId = userContext.requireUserId();
        return ApiResponse.ok(Map.of(
                "venue", seckillService.myOrders(userId, 1),
                "equipment", seckillService.myOrders(userId, 2)
        ));
    }

    @PostMapping("/preload")
    public ApiResponse<Map<String, Object>> preload() {
        userContext.require();
        return ApiResponse.ok(seckillService.preload());
    }
}
