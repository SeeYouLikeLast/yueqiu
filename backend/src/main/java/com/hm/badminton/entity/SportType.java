package com.hm.badminton.entity;

import java.util.List;

public record SportType(
        String code,
        String name,
        List<String> keywords) {
}

