package com.hm.badminton.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class VenueCartItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long productId;
    private Long inventoryId;
    private String productName;
    private String venueName;
    private String coverUrl;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal amount;
    private LocalDate serviceDate;
    private LocalTime startTime;
    private LocalTime endTime;
}
