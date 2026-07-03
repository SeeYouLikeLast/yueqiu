package com.hm.badminton.controller.trade;

import com.hm.badminton.common.ApiResponse;
import com.hm.badminton.constants.TradeType;
import com.hm.badminton.service.trade.IEquipmentService;
import com.hm.badminton.service.trade.IVenueItemService;
import com.hm.badminton.service.trade.impl.EquipmentService;
import com.hm.badminton.service.trade.impl.VenueItemService;
import com.hm.badminton.utils.UserContext;
import com.hm.badminton.vo.CartItemVO;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/cart")
public class CartController {

    private final IEquipmentService equipmentService;
    private final IVenueItemService venueItemService;
    private final UserContext userContext;

    public CartController(IEquipmentService equipmentService,
                          IVenueItemService venueItemService,
                          UserContext userContext) {
        this.equipmentService = equipmentService;
        this.venueItemService = venueItemService;
        this.userContext = userContext;
    }

    @PostMapping
    public ApiResponse<Void> add(@RequestBody CartRequest request) {
        Long userId = userContext.requireUserId();
        int type = TradeType.require(request.getType());
        if (type == TradeType.VENUE) {
            venueItemService.addCart(userId, new VenueItemService.OrderCreateRequest(request.getProductId(), request.getInventoryId()));
            return ApiResponse.ok();
        }
        equipmentService.addCart(userId, new EquipmentService.CartRequest(request.getProductId(), quantity(request.getQuantity())));
        return ApiResponse.ok();
    }

    @GetMapping
    public ApiResponse<List<CartItemVO>> list() {
        Long userId = userContext.requireUserId();
        List<CartItemVO> result = new ArrayList<>();
        result.addAll(venueItemService.cart(userId).stream().map(CartItemVO::fromVenue).toList());
        result.addAll(equipmentService.cart(userId).stream().map(CartItemVO::fromEquipment).toList());
        return ApiResponse.ok(result);
    }

    @DeleteMapping("/{type:[12]}/{itemId}")
    public ApiResponse<Void> remove(@PathVariable Integer type, @PathVariable Long itemId) {
        Long userId = userContext.requireUserId();
        int tradeType = TradeType.require(type);
        if (tradeType == TradeType.VENUE) {
            venueItemService.removeCart(userId, itemId);
        } else {
            equipmentService.removeCart(userId, itemId);
        }
        return ApiResponse.ok();
    }

    private int quantity(Integer value) {
        return value == null || value < 1 ? 1 : value;
    }

    @Data
    public static class CartRequest {
        @NotNull
        private Integer type;
        @NotNull
        private Long productId;
        private Long inventoryId;
        @Min(1)
        private Integer quantity;
    }
}


