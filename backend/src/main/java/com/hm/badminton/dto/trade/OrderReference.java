package com.hm.badminton.dto.trade;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Internal database id plus the public UUID order number. */
@Data
@AllArgsConstructor
public class OrderReference {
    private Long id;
    private String orderNo;
}
