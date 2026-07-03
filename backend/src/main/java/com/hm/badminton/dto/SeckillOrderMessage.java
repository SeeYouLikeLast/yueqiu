package com.hm.badminton.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillOrderMessage {
    private Long orderId;
    private Integer type;
    private Long activityId;
    private Long productId;
    private Long userId;
    private BigDecimal amount;
}

