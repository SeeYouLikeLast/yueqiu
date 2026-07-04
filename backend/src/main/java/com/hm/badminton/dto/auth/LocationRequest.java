package com.hm.badminton.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LocationRequest {
    private String city;
    private String preciseAddress;
    private Double lng;
    private Double lat;
}
