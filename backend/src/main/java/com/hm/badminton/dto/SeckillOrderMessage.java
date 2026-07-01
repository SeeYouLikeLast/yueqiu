package com.hm.badminton.dto;

import java.math.BigDecimal;

public record SeckillOrderMessage(
        Long orderId,
        Long activityId,
        Long productId,
        Long userId,
        BigDecimal amount) {
}
