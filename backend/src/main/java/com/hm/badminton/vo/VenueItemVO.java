package com.hm.badminton.vo;

import com.hm.badminton.entity.VenueItem;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VenueItemVO {
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
    private Boolean purchasable;
    private LocalDateTime saleStartAt;
    private LocalDateTime saleEndAt;

    public static VenueItemVO from(VenueItem item) {
        if (item == null) {
            return null;
        }
        return new VenueItemVO(
                item.getId(),
                item.getVenueId(),
                item.getAmapPlaceId(),
                item.getVenueName(),
                item.getPlaceRank(),
                item.getSportCode(),
                item.getProductType(),
                item.getProductTypeName(),
                item.getTitle(),
                item.getDescription(),
                item.getCoverUrl(),
                item.getPrice(),
                item.getOriginalPrice(),
                item.getTags(),
                item.getUseRule(),
                item.getRefundRule(),
                item.getAvailableStock() != null && item.getAvailableStock() > 0,
                item.getSaleStartAt(),
                item.getSaleEndAt());
    }
}

