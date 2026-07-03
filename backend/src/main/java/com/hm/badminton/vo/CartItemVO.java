package com.hm.badminton.vo;

import com.hm.badminton.entity.CartItem;
import com.hm.badminton.entity.VenueCartItem;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartItemVO {
    private Long id;
    private Integer type;
    private Long productId;
    private Long inventoryId;
    private String productName;
    private String brand;
    private String coverUrl;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal amount;
    private String meta;

    public static CartItemVO fromEquipment(CartItem item) {
        if (item == null) {
            return null;
        }
        return new CartItemVO(
                item.getId(),
                2,
                item.getProductId(),
                null,
                item.getProductName(),
                item.getBrand(),
                item.getCoverUrl(),
                item.getPrice(),
                item.getQuantity(),
                item.getAmount(),
                "装备商品");
    }

    public static CartItemVO fromVenue(VenueCartItem item) {
        if (item == null) {
            return null;
        }
        String time = item.getServiceDate() + " " + item.getStartTime() + "-" + item.getEndTime();
        return new CartItemVO(
                item.getId(),
                1,
                item.getProductId(),
                item.getInventoryId(),
                item.getProductName(),
                item.getVenueName(),
                item.getCoverUrl(),
                item.getPrice(),
                item.getQuantity(),
                item.getAmount(),
                time);
    }
}

