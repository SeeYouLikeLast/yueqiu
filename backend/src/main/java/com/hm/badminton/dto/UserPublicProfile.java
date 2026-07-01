package com.hm.badminton.dto;

import java.time.LocalDateTime;

public record UserPublicProfile(
        Long id,
        String nickname,
        String avatar,
        String city,
        String level,
        String preferTime,
        LocalDateTime createdAt,
        boolean followed,
        boolean isMe) {
}
