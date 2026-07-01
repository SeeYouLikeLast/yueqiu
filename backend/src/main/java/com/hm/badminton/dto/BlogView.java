package com.hm.badminton.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record BlogView(
        Long id,
        Long userId,
        String nickname,
        String avatar,
        String sportCode,
        String title,
        String content,
        List<String> images,
        String relatedType,
        Long relatedId,
        String relatedTitle,
        String relatedCoverUrl,
        BigDecimal relatedPrice,
        Integer liked,
        Boolean isLiked,
        Boolean followed,
        LocalDateTime createdAt) {
}
