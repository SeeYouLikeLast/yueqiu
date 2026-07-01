package com.hm.badminton.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderSummary(
        Long id,
        Long userId,
        BigDecimal totalAmount,
        String status,
        String address,
        LocalDateTime createdAt,
        List<OrderItem> items) {

    public record OrderItem(Long productId, String productName, String coverUrl, BigDecimal price, Integer quantity) {
    }
}

