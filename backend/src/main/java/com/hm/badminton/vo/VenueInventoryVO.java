package com.hm.badminton.vo;

import com.hm.badminton.entity.VenueInventory;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VenueInventoryVO {
    private Long id;
    private Long productId;
    private String courtName;
    private Long coachId;
    private String coachName;
    private LocalDate serviceDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private BigDecimal price;
    private Boolean purchasable;

    public static VenueInventoryVO from(VenueInventory inventory) {
        if (inventory == null) {
            return null;
        }
        return new VenueInventoryVO(
                inventory.getId(),
                inventory.getProductId(),
                inventory.getCourtName(),
                inventory.getCoachId(),
                inventory.getCoachName(),
                inventory.getServiceDate(),
                inventory.getStartTime(),
                inventory.getEndTime(),
                inventory.getPrice(),
                inventory.getAvailableStock() != null && inventory.getAvailableStock() > 0);
    }
}

