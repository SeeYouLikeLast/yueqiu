package com.hm.badminton.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SeckillOrder(
        Long id,
        Long activityId,
        Long productId,
        String productName,
        Long userId,
        BigDecimal amount,
        String status,
        LocalDateTime createdAt) {
}

