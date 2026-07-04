package com.hm.badminton.dto.trade;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EquipmentCreateRequest {
    private Long categoryId;
    private String sportCode;
    @NotBlank
    private String name;
    @NotBlank
    private String brand;
    private String description;
    private String coverUrl;
    private BigDecimal price;
    private Integer stock;
}
