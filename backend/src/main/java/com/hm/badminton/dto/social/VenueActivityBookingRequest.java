package com.hm.badminton.dto.social;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 用户确认“购买场馆时段并发起约球”时提交的请求。
 * 商品和库存 ID 是交易事实，场所信息用于把活动绑定到当前高德 POI。
 */
@Data
public class VenueActivityBookingRequest {

    @NotNull
    private Long productId;

    @NotNull
    private Long inventoryId;

    @NotBlank
    private String placeId;

    private String placeSource;

    @NotBlank
    private String venueName;

    @NotBlank
    private String city;

    @Min(2)
    @Max(20)
    private Integer maxPlayers = 4;

    private String levelRequired;
}
