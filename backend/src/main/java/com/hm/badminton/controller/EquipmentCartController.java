package com.hm.badminton.controller;

import com.hm.badminton.common.ApiResponse;
import com.hm.badminton.entity.CartItem;
import com.hm.badminton.service.IEquipmentService;
import com.hm.badminton.service.impl.EquipmentService;
import com.hm.badminton.utils.UserContext;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/equipment/cart")
public class EquipmentCartController {

    private final IEquipmentService equipmentService;
    private final UserContext userContext;

    public EquipmentCartController(IEquipmentService equipmentService, UserContext userContext) {
        this.equipmentService = equipmentService;
        this.userContext = userContext;
    }

    @PostMapping
    public ApiResponse<Void> addCart(@Valid @RequestBody EquipmentService.CartRequest request) {
        equipmentService.addCart(userContext.requireUserId(), request);
        return ApiResponse.ok();
    }

    @GetMapping
    public ApiResponse<List<CartItem>> cart() {
        return ApiResponse.ok(equipmentService.cart(userContext.requireUserId()));
    }

    @DeleteMapping("/{itemId}")
    public ApiResponse<Void> removeCart(@PathVariable Long itemId) {
        equipmentService.removeCart(userContext.requireUserId(), itemId);
        return ApiResponse.ok();
    }
}
