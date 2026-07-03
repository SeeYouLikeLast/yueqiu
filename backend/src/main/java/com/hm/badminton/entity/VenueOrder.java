package com.hm.badminton.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("order_venue")
public class VenueOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private Long productId;
    private Long inventoryId;
    private Long venueId;
    private String amapPlaceId;
    private String venueName;
    private String productTitle;
    private String productType;
    private LocalDate serviceDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private BigDecimal amount;
    private String status;
    private String verifyCode;
    private LocalDateTime paidAt;
    private LocalDateTime usedAt;
    private LocalDateTime createdAt;
}

