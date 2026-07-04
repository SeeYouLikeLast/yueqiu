package com.hm.badminton.service.trade;

import com.hm.badminton.dto.trade.PaymentRequest;

import java.util.Map;

public interface ITradeOrderService {

    Map<String, Object> payDirect(Long userId, PaymentRequest request);

    Map<String, Object> payCart(Long userId, PaymentRequest request);
}
