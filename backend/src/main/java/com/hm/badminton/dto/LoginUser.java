package com.hm.badminton.dto;

public record LoginUser(Long id,
                        String phone,
                        String nickname,
                        String city,
                        String level,
                        Double longitude,
                        Double latitude,
                        String preciseAddress) {

    public LoginUser(Long id, String phone, String nickname, String city, String level) {
        this(id, phone, nickname, city, level, null, null, null);
    }
}

