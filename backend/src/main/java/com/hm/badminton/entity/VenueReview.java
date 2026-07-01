package com.hm.badminton.entity;

import java.time.LocalDateTime;

public record VenueReview(
        Long id,
        Long venueId,
        Long userId,
        String nickname,
        String avatar,
        Integer rating,
        String content,
        String imageUrls,
        Integer likes,
        LocalDateTime createdAt) {
}

