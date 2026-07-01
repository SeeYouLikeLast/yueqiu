package com.hm.badminton.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Venue(
        Long id,
        String name,
        String city,
        String area,
        String address,
        BigDecimal longitude,
        BigDecimal latitude,
        Integer avgPrice,
        BigDecimal score,
        Integer reviewCount,
        String openHours,
        String coverUrl,
        String facilities,
        Double distanceMeters,
        LocalDateTime createdAt) {
}

