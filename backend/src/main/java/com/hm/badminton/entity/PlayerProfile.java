package com.hm.badminton.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class PlayerProfile implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private String nickname;
    private String avatar;
    private String sportCode;
    private String city;
    private String area;
    private Double longitude;
    private Double latitude;
    private String level;
    private String playStyle;
    private String availableTime;
    private String intro;
    private Boolean allowInvite;
    private Double distanceMeters;
}

