package com.hm.badminton.entity;

import java.util.List;

public record AmapPlace(
        String id,
        String name,
        String sportCode,
        String sportName,
        String city,
        String area,
        String address,
        Double longitude,
        Double latitude,
        Double distanceMeters,
        String type,
        String tel,
        String businessArea,
        String openHours,
        String coverUrl,
        List<String> facilities,
        String source) {
}

