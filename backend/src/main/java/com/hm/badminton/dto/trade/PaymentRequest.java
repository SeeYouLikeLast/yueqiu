package com.hm.badminton.dto.trade;

import lombok.Data;

@Data
public class PaymentRequest {
    /** Required by direct payment; ignored by the endpoint that settles both carts. */
    private Integer type;
    private Long productId;
    private Long inventoryId;
    private Integer quantity;
    private String address;
}
