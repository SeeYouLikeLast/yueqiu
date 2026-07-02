package com.hm.badminton.vo;

import com.hm.badminton.entity.SeckillActivity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillActivityVO {
    private Long id;
    private Integer type;
    private Long productId;
    private String productName;
    private String sportCode;
    private Long categoryId;
    private String categoryName;
    private String coverUrl;
    private BigDecimal originalPrice;
    private BigDecimal seckillPrice;
    private Boolean purchasable;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Integer status;

    public static SeckillActivityVO from(SeckillActivity activity) {
        if (activity == null) {
            return null;
        }
        return new SeckillActivityVO(
                activity.getId(),
                activity.getType(),
                activity.getProductId(),
                activity.getProductName(),
                activity.getSportCode(),
                activity.getCategoryId(),
                activity.getCategoryName(),
                activity.getCoverUrl(),
                activity.getOriginalPrice(),
                activity.getSeckillPrice(),
                activity.getStock() != null && activity.getStock() > 0,
                activity.getStartAt(),
                activity.getEndAt(),
                activity.getStatus());
    }
}
