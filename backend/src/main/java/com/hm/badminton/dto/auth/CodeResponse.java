package com.hm.badminton.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodeResponse {
    private String email;
    private long expireSeconds;
    private long cooldownSeconds;
}
