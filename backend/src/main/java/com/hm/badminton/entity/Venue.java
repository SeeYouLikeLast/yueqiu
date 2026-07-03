package com.hm.badminton.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class Venue implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String city;
    private String area;
    private String address;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private Integer avgPrice;
    private BigDecimal score;
    private Integer reviewCount;
    private String openHours;
    private String coverUrl;
    private String facilities;
    private Double distanceMeters;
    private LocalDateTime createdAt;
}

