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
public class SeckillActivity implements Serializable {

    private static final long serialVersionUID = 1L;

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
    private Integer stock;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Integer status;
}

