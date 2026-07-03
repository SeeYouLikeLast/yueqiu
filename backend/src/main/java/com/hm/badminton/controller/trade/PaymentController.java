package com.hm.badminton.controller.trade;

import com.hm.badminton.common.ApiResponse;
import com.hm.badminton.common.BusinessException;
import com.hm.badminton.constants.TradeType;
import com.hm.badminton.entity.VenueCartItem;
import com.hm.badminton.entity.VenueOrder;
import com.hm.badminton.service.trade.IEquipmentService;
import com.hm.badminton.service.trade.IVenueItemService;
import com.hm.badminton.service.trade.impl.EquipmentService;
import com.hm.badminton.service.trade.impl.VenueItemService;
import com.hm.badminton.utils.UserContext;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final IVenueItemService venueItemService;
    private final IEquipmentService equipmentService;
    private final UserContext userContext;

    public PaymentController(IVenueItemService venueItemService,
                             IEquipmentService equipmentService,
                             UserContext userContext) {
        this.venueItemService = venueItemService;
        this.equipmentService = equipmentService;
        this.userContext = userContext;
    }

    @PostMapping
    public ApiResponse<?> pay(@RequestBody PaymentRequest request) {
        Long userId = userContext.requireUserId();
        int tradeType = TradeType.require(request.getType());
        if (tradeType == TradeType.VENUE) {
            VenueOrder created = venueItemService.createOrder(userId,
                    new VenueItemService.OrderCreateRequest(request.getProductId(), request.getInventoryId()));
            VenueOrder paid = venueItemService.pay(userId, created.getId());
            return ApiResponse.ok(Map.of(
                    "orderId", paid.getId(),
                    "verifyCode", paid.getVerifyCode(),
                    "order", paid));
        }
        List<EquipmentService.OrderItemRequest> items = request.getProductId() == null
                ? null
                : List.of(new EquipmentService.OrderItemRequest(request.getProductId(), quantity(request.getQuantity())));
        Long orderId = equipmentService.createOrder(userId,
                new EquipmentService.CreateOrderRequest(items, request.getAddress()));
        equipmentService.pay(userId, orderId);
        return ApiResponse.ok(Map.of("orderId", orderId));
    }

    @PostMapping("/cart")
    @Transactional
    public ApiResponse<Map<String, Object>> payCart(@RequestBody PaymentRequest request) {
        Long userId = userContext.requireUserId();
        List<VenueCartItem> venueItems = venueItemService.cart(userId);
        List<Long> venueOrderIds = new ArrayList<>();
        for (VenueCartItem item : venueItems) {
            VenueOrder created = venueItemService.createOrder(userId,
                    new VenueItemService.OrderCreateRequest(item.getProductId(), item.getInventoryId()));
            VenueOrder paid = venueItemService.pay(userId, created.getId());
            venueOrderIds.add(paid.getId());
        }
        if (!venueItems.isEmpty()) {
            venueItemService.clearCart(userId);
        }

        Long equipmentOrderId = null;
        if (!equipmentService.cart(userId).isEmpty()) {
            equipmentOrderId = equipmentService.createOrder(userId,
                    new EquipmentService.CreateOrderRequest(null, request.getAddress()));
            equipmentService.pay(userId, equipmentOrderId);
        }

        if (venueOrderIds.isEmpty() && equipmentOrderId == null) {
            throw new BusinessException("购物车为空");
        }
        return ApiResponse.ok(Map.of(
                "venueOrderIds", venueOrderIds,
                "equipmentOrderId", equipmentOrderId == null ? "" : equipmentOrderId));
    }

    private int quantity(Integer value) {
        return value == null || value < 1 ? 1 : value;
    }

    @Data
    public static class PaymentRequest {
        @NotNull
        private Integer type;
        private Long productId;
        private Long inventoryId;
        private Integer quantity;
        private String address;
    }
}


