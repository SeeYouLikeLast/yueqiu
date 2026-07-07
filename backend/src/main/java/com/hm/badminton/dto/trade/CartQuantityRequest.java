package com.hm.badminton.dto.trade;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartQuantityRequest {
    @NotNull
    @Min(1)
    @Max(99)
    private Integer quantity;
}
