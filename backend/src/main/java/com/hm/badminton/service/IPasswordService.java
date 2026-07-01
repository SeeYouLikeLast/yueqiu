package com.hm.badminton.service;

public interface IPasswordService {
    String encode(String rawPassword);

    boolean matches(String rawPassword, String storedPassword);
}
