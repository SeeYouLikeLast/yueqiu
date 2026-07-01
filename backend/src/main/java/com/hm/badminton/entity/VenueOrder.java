package com.hm.badminton.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@TableName("venue_orders")
public record VenueOrder(
        Long id,
        Long userId,
        Long productId,
        Long inventoryId,
        Long venueId,
        String amapPlaceId,
        String venueName,
        String productTitle,
        String productType,
        LocalDate serviceDate,
        LocalTime startTime,
        LocalTime endTime,
        BigDecimal amount,
        String status,
        String verifyCode,
        LocalDateTime paidAt,
        LocalDateTime usedAt,
        LocalDateTime createdAt) {
}

