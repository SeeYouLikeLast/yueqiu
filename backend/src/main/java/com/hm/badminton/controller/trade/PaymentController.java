package com.hm.badminton.controller.trade;

import com.hm.badminton.common.ApiResponse;
import com.hm.badminton.dto.trade.PaymentRequest;
import com.hm.badminton.dto.trade.PaymentResult;
import com.hm.badminton.service.trade.ITradeOrderService;
import com.hm.badminton.utils.UserContext;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 立即购买和购物车结算的统一模拟支付入口。 */
@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final ITradeOrderService tradeOrderService;
    private final UserContext userContext;

    public PaymentController(ITradeOrderService tradeOrderService,
                             UserContext userContext) {
        this.tradeOrderService = tradeOrderService;
        this.userContext = userContext;
    }

    // Controller 只负责参数入口，交易事务统一下沉到 Service 层。
    @PostMapping
    public ApiResponse<PaymentResult> pay(@Valid @RequestBody PaymentRequest request) {
        Long userId = userContext.requireUserId();
        return ApiResponse.ok(tradeOrderService.payDirect(userId, request));
    }

    @PostMapping("/cart")
    public ApiResponse<PaymentResult> payCart(@Valid @RequestBody PaymentRequest request) {
        Long userId = userContext.requireUserId();
        return ApiResponse.ok(tradeOrderService.payCart(userId, request));
    }
}


