package com.hm.badminton.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser {
    private Long id;
    private String phone;
    private String nickname;
    private String city;
    private String level;
    private Double longitude;
    private Double latitude;
    private String preciseAddress;

    public LoginUser(Long id, String phone, String nickname, String city, String level) {
        this(id, phone, nickname, city, level, null, null, null);
    }
}

