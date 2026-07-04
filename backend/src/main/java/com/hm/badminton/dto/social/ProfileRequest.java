package com.hm.badminton.dto.social;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfileRequest {
    private String sportCode;

    @NotBlank
    private String city;

    @NotBlank
    private String area;

    private Double longitude;
    private Double latitude;

    @NotBlank
    private String level;

    private String playStyle;
    private String availableTime;
    private String intro;
    private Boolean allowInvite;
}
