package com.hm.badminton.entity;

import java.math.BigDecimal;

public record CartItem(
        Long id,
        Long productId,
        String productName,
        String brand,
        String coverUrl,
        BigDecimal price,
        Integer quantity,
        Integer stock,
        BigDecimal amount) {
}

