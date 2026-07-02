package com.hm.badminton.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class AmapPlace implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String sportCode;
    private String sportName;
    private String city;
    private String area;
    private String address;
    private Double longitude;
    private Double latitude;
    private Double distanceMeters;
    private String type;
    private String tel;
    private String businessArea;
    private String openHours;
    private String coverUrl;
    private List<String> facilities;
    private String source;
}
