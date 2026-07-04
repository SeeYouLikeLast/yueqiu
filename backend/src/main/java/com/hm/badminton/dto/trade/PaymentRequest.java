package com.hm.badminton.dto.trade;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PaymentRequest {
    @NotNull
    private Integer type;
    private Long productId;
    private Long inventoryId;
    private Integer quantity;
    private String address;
}
