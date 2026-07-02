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
public class SeckillOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Integer type;
    private Long activityId;
    private Long productId;
    private String productName;
    private Long userId;
    private BigDecimal amount;
    private String status;
    private LocalDateTime createdAt;
}
