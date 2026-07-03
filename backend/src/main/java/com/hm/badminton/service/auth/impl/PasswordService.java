package com.hm.badminton.service.auth.impl;

import com.hm.badminton.service.auth.IPasswordService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordService implements IPasswordService {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

    public String encode(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String storedPassword) {
        if (storedPassword != null && storedPassword.startsWith("{plain}")) {
            return storedPassword.substring("{plain}".length()).equals(rawPassword);
        }
        return storedPassword != null && encoder.matches(rawPassword, storedPassword);
    }
}



