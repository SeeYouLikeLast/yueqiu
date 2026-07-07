package com.hm.badminton.dto.auth;

import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    private String account;
    private String password;
    private String phone;
    @Email(message = "邮箱格式不正确")
    private String email;
    private String code;
}
