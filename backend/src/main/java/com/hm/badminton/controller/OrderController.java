package com.hm.badminton.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.badminton.common.ApiResponse;
import com.hm.badminton.constants.TradeType;
import com.hm.badminton.entity.OrderSummary;
import com.hm.badminton.entity.VenueOrder;
import com.hm.badminton.service.IEquipmentService;
import com.hm.badminton.service.IVenueItemService;
import com.hm.badminton.service.impl.EquipmentService;
import com.hm.badminton.service.impl.VenueItemService;
import com.hm.badminton.utils.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final IVenueItemService venueItemService;
    private final IEquipmentService equipmentService;
    private final UserContext userContext;
    private final ObjectMapper objectMapper;

    public OrderController(IVenueItemService venueItemService,
                           IEquipmentService equipmentService,
                           UserContext userContext,
                           ObjectMapper objectMapper) {
        this.venueItemService = venueItemService;
        this.equipmentService = equipmentService;
        this.userContext = userContext;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/{type:[12]}")
    public ApiResponse<?> create(@PathVariable Integer type, @RequestBody Object body) {
        Long userId = userContext.requireUserId();
        int tradeType = TradeType.require(type);
        if (tradeType == TradeType.VENUE) {
            VenueItemService.OrderCreateRequest request = objectMapper.convertValue(body, VenueItemService.OrderCreateRequest.class);
            var order = venueItemService.createOrder(userId, request);
            return ApiResponse.ok(Map.of("orderId", order.getId(), "verifyCode", order.getVerifyCode(), "order", order));
        }
        EquipmentService.CreateOrderRequest request = objectMapper.convertValue(body, EquipmentService.CreateOrderRequest.class);
        return ApiResponse.ok(Map.of("orderId", equipmentService.createOrder(userId, request)));
    }

    @PostMapping("/{type:[12]}/{orderId}/pay")
    public ApiResponse<?> pay(@PathVariable Integer type, @PathVariable Long orderId) {
        Long userId = userContext.requireUserId();
        int tradeType = TradeType.require(type);
        if (tradeType == TradeType.VENUE) {
            return ApiResponse.ok(venueItemService.pay(userId, orderId));
        }
        equipmentService.pay(userId, orderId);
        return ApiResponse.ok();
    }

    @GetMapping("/{type:[12]}")
    public ApiResponse<?> orders(@PathVariable Integer type) {
        Long userId = userContext.requireUserId();
        int tradeType = TradeType.require(type);
        return ApiResponse.ok(tradeType == TradeType.VENUE
                ? venueItemService.myOrders(userId)
                : equipmentService.orders(userId));
    }

    @GetMapping("/all")
    public ApiResponse<Map<String, Object>> all() {
        Long userId = userContext.requireUserId();
        List<VenueOrder> venue = venueItemService.myOrders(userId);
        List<OrderSummary> equipment = equipmentService.orders(userId);
        return ApiResponse.ok(Map.of("venue", venue, "equipment", equipment));
    }
}
