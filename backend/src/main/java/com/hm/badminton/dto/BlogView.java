package com.hm.badminton.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlogView {
    private Long id;
    private Long userId;
    private String nickname;
    private String avatar;
    private String sportCode;
    private String title;
    private String content;
    private List<String> images;
    private String relatedType;
    private Long relatedId;
    private String relatedTitle;
    private String relatedCoverUrl;
    private BigDecimal relatedPrice;
    private Integer liked;
    private Boolean isLiked;
    private Boolean followed;
    private LocalDateTime createdAt;
}

