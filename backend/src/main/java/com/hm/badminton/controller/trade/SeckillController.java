package com.hm.badminton.controller.trade;

import com.hm.badminton.utils.UserContext;
import com.hm.badminton.common.ApiResponse;
import com.hm.badminton.service.trade.ISeckillService;
import com.hm.badminton.vo.SeckillActivityVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 场馆商品/装备秒杀活动列表与抢购入口。 */
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

}


