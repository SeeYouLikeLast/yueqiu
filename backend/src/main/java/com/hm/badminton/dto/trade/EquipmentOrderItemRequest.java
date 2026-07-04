package com.hm.badminton.dto.trade;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EquipmentOrderItemRequest {
    private Long productId;
    @Min(1)
    private Integer quantity;
}
