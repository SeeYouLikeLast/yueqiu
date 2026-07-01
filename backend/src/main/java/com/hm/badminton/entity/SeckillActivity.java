package com.hm.badminton.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SeckillActivity(
        Long id,
        Long productId,
        String productName,
        String sportCode,
        Long categoryId,
        String categoryName,
        String coverUrl,
        BigDecimal originalPrice,
        BigDecimal seckillPrice,
        Integer stock,
        LocalDateTime startAt,
        LocalDateTime endAt,
        Integer status) {
}

