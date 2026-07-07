package com.hm.badminton.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfileUpdateRequest {
    private String phone;
    private String email;
    private String username;
    private String password;
    private String nickname;
    private String avatar;
    private String city;
    private String level;
    private String preferTime;
}
