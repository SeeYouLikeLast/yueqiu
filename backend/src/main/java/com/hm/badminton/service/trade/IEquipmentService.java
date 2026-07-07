package com.hm.badminton.service.trade;

import com.hm.badminton.common.PageResult;
import com.hm.badminton.dto.trade.EquipmentCartRequest;
import com.hm.badminton.dto.trade.EquipmentCreateRequest;
import com.hm.badminton.dto.trade.EquipmentOrderCreateRequest;
import com.hm.badminton.entity.CartItem;
import com.hm.badminton.entity.OrderSummary;
import com.hm.badminton.entity.Equipment;
import com.hm.badminton.entity.EquipmentCategory;

import java.util.List;

public interface IEquipmentService {
    List<EquipmentCategory> categories(String sportCode);

    PageResult<Equipment> items(String sportCode, Long categoryId, String keyword, int page, int size);

    Equipment detail(Long id);

    void addCart(Long userId, EquipmentCartRequest request);

    List<CartItem> cart(Long userId);

    void removeCart(Long userId, Long itemId);

    void updateCartQuantity(Long userId, Long itemId, Integer quantity);

    void clearCart(Long userId);

    Long createOrder(Long userId, EquipmentOrderCreateRequest request);

    void pay(Long userId, Long orderId);

    List<OrderSummary> orders(Long userId);

    Long createEquipment(EquipmentCreateRequest request);
}


