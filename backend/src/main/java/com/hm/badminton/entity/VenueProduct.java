package com.hm.badminton.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record VenueProduct(
        Long id,
        Long venueId,
        String amapPlaceId,
        String venueName,
        Integer placeRank,
        String sportCode,
        String productType,
        String productTypeName,
        String title,
        String description,
        String coverUrl,
        BigDecimal price,
        BigDecimal originalPrice,
        List<String> tags,
        String useRule,
        String refundRule,
        Integer availableStock,
        LocalDateTime saleStartAt,
        LocalDateTime saleEndAt) {
}

