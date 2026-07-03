package com.hm.badminton.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPublicProfile {
    private Long id;
    private String nickname;
    private String avatar;
    private String city;
    private String level;
    private String preferTime;
    private LocalDateTime createdAt;
    private boolean followed;
    private boolean isMe;
}

