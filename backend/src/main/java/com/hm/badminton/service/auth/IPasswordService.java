package com.hm.badminton.service.auth;

public interface IPasswordService {
    String encode(String rawPassword);

    boolean matches(String rawPassword, String storedPassword);
}


