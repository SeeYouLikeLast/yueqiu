package com.hm.badminton.dto.trade;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VenueOrderCreateRequest {
    @NotNull
    private Long productId;
    @NotNull
    private Long inventoryId;
}
