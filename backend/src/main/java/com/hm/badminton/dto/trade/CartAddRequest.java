package com.hm.badminton.dto.trade;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CartAddRequest {
    @NotNull
    private Integer type;
    @NotNull
    private Long productId;
    private Long inventoryId;
    @Min(1)
    private Integer quantity;
}
