package com.hm.badminton.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Product(
        Long id,
        String sportCode,
        Long categoryId,
        String categoryName,
        String name,
        String brand,
        String description,
        String coverUrl,
        BigDecimal price,
        Integer stock,
        java.math.BigDecimal score,
        Integer sold,
        Integer status,
        LocalDateTime createdAt) {
}

