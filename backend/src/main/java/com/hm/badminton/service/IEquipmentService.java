package com.hm.badminton.service;

import com.hm.badminton.common.PageResult;
import com.hm.badminton.entity.CartItem;
import com.hm.badminton.entity.OrderSummary;
import com.hm.badminton.entity.Equipment;
import com.hm.badminton.entity.EquipmentCategory;
import com.hm.badminton.service.impl.EquipmentService;

import java.util.List;

public interface IEquipmentService {
    List<EquipmentCategory> categories(String sportCode);

    PageResult<Equipment> items(String sportCode, Long categoryId, String keyword, int page, int size);

    Equipment detail(Long id);

    void addCart(Long userId, EquipmentService.CartRequest request);

    List<CartItem> cart(Long userId);

    void removeCart(Long userId, Long itemId);

    void clearCart(Long userId);

    Long createOrder(Long userId, EquipmentService.CreateOrderRequest request);

    void pay(Long userId, Long orderId);

    List<OrderSummary> orders(Long userId);

    Long createEquipment(EquipmentService.EquipmentCreateRequest request);
}
