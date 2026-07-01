package com.hm.badminton.entity;

public record PlayerProfile(
        Long userId,
        String nickname,
        String avatar,
        String sportCode,
        String city,
        String area,
        Double longitude,
        Double latitude,
        String level,
        String playStyle,
        String availableTime,
        String intro,
        Boolean allowInvite,
        Double distanceMeters) {
}

