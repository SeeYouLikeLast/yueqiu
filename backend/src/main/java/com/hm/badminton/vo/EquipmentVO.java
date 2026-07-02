package com.hm.badminton.vo;

import com.hm.badminton.entity.Equipment;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EquipmentVO {
    private Long id;
    private String sportCode;
    private Long categoryId;
    private String categoryName;
    private String name;
    private String brand;
    private String description;
    private String coverUrl;
    private BigDecimal price;
    private BigDecimal score;
    private Integer sold;

    public static EquipmentVO from(Equipment equipment) {
        if (equipment == null) {
            return null;
        }
        return new EquipmentVO(
                equipment.getId(),
                equipment.getSportCode(),
                equipment.getCategoryId(),
                equipment.getCategoryName(),
                equipment.getName(),
                equipment.getBrand(),
                equipment.getDescription(),
                equipment.getCoverUrl(),
                equipment.getPrice(),
                equipment.getScore(),
                equipment.getSold());
    }
}
