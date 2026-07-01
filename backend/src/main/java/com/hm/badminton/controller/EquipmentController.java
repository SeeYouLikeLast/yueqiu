package com.hm.badminton.controller;

import com.hm.badminton.utils.UserContext;
import com.hm.badminton.common.ApiResponse;
import com.hm.badminton.common.PageResult;
import com.hm.badminton.entity.CartItem;
import com.hm.badminton.entity.OrderSummary;
import com.hm.badminton.entity.Product;
import com.hm.badminton.entity.ProductCategory;
import com.hm.badminton.service.IEquipmentService;
import com.hm.badminton.service.impl.EquipmentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/equipment")
public class EquipmentController {

    private final IEquipmentService equipmentService;
    private final UserContext userContext;

    public EquipmentController(IEquipmentService equipmentService, UserContext userContext) {
        this.equipmentService = equipmentService;
        this.userContext = userContext;
    }

    @GetMapping("/categories")
    public ApiResponse<List<ProductCategory>> categories(@RequestParam(required = false) String sport) {
        return ApiResponse.ok(equipmentService.categories(sport));
    }

    @GetMapping("/products")
    public ApiResponse<PageResult<Product>> products(@RequestParam(required = false) String sport,
                                                     @RequestParam(required = false) Long categoryId,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "12") int size) {
        return ApiResponse.ok(equipmentService.products(sport, categoryId, keyword, page, size));
    }

    @GetMapping("/products/{id}")
    public ApiResponse<Product> detail(@PathVariable Long id) {
        return ApiResponse.ok(equipmentService.detail(id));
    }

    @PostMapping("/cart")
    public ApiResponse<Void> addCart(@Valid @RequestBody EquipmentService.CartRequest request) {
        equipmentService.addCart(userContext.requireUserId(), request);
        return ApiResponse.ok();
    }

    @GetMapping("/cart")
    public ApiResponse<List<CartItem>> cart() {
        return ApiResponse.ok(equipmentService.cart(userContext.requireUserId()));
    }

    @DeleteMapping("/cart/{itemId}")
    public ApiResponse<Void> removeCart(@PathVariable Long itemId) {
        equipmentService.removeCart(userContext.requireUserId(), itemId);
        return ApiResponse.ok();
    }

    @PostMapping("/orders")
    public ApiResponse<Map<String, Long>> createOrder(@Valid @RequestBody EquipmentService.CreateOrderRequest request) {
        return ApiResponse.ok(Map.of("orderId", equipmentService.createOrder(userContext.requireUserId(), request)));
    }

    @PostMapping("/orders/{orderId}/pay")
    public ApiResponse<Void> pay(@PathVariable Long orderId) {
        equipmentService.pay(userContext.requireUserId(), orderId);
        return ApiResponse.ok();
    }

    @GetMapping("/orders")
    public ApiResponse<List<OrderSummary>> orders() {
        return ApiResponse.ok(equipmentService.orders(userContext.requireUserId()));
    }
}

