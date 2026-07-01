package com.hm.badminton.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record VenueProductInventory(
        Long id,
        Long productId,
        String courtName,
        Long coachId,
        String coachName,
        LocalDate serviceDate,
        LocalTime startTime,
        LocalTime endTime,
        Integer totalStock,
        Integer availableStock,
        Integer soldStock,
        BigDecimal price,
        String status) {
}

