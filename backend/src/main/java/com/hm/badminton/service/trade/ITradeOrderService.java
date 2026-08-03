package com.hm.badminton.service.trade;

import com.hm.badminton.dto.trade.PaymentRequest;
import com.hm.badminton.dto.trade.PaymentResult;

public interface ITradeOrderService {

    PaymentResult payDirect(Long userId, PaymentRequest request);

    PaymentResult payCart(Long userId, PaymentRequest request);
}
