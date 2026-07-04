package com.hm.badminton.dto.auth;

import com.hm.badminton.dto.LoginUser;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private LoginUser user;
}
