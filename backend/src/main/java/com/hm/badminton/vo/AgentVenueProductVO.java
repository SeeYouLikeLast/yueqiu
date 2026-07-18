package com.hm.badminton.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/** A purchasable venue product joined with one concrete inventory slot. */
@Data
public class AgentVenueProductVO {
    private Long productId;
    private Long inventoryId;
    private Long venueId;
    private String amapPlaceId;
    private String venueName;
    private Integer placeRank;
    private String sportCode;
    private String productType;
    private String title;
    private String description;
    private String coverUrl;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private String tags;
    private String useRule;
    private String refundRule;
    private LocalDate serviceDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer availableStock;
    private String matchType;
}
