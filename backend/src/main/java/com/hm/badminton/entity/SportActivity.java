package com.hm.badminton.entity;

import java.time.LocalDateTime;

public record SportActivity(
        Long id,
        Long creatorId,
        String creatorName,
        String sportCode,
        Long venueId,
        String placeSource,
        String placeId,
        String venueName,
        String title,
        String city,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer maxPlayers,
        Integer currentPlayers,
        String levelRequired,
        String feeType,
        String status,
        LocalDateTime createdAt) {
}

