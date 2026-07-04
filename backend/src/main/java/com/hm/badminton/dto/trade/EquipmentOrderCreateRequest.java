package com.hm.badminton.dto.trade;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EquipmentOrderCreateRequest {
    private List<EquipmentOrderItemRequest> items;
    @NotBlank
    private String address;
}
