package com.hm.badminton.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class VenueItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long venueId;
    private String amapPlaceId;
    private String venueName;
    private Integer placeRank;
    private String sportCode;
    private String productType;
    private String productTypeName;
    private String title;
    private String description;
    private String coverUrl;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private List<String> tags;
    private String useRule;
    private String refundRule;
    private Integer availableStock;
    private LocalDateTime saleStartAt;
    private LocalDateTime saleEndAt;
}
