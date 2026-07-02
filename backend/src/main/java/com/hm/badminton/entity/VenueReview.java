package com.hm.badminton.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class VenueReview implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long venueId;
    private Long userId;
    private String nickname;
    private String avatar;
    private Integer rating;
    private String content;
    private String imageUrls;
    private Integer likes;
    private LocalDateTime createdAt;
}
