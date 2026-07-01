package com.hm.badminton.service;

import com.hm.badminton.common.PageResult;
import com.hm.badminton.entity.CartItem;
import com.hm.badminton.entity.OrderSummary;
import com.hm.badminton.entity.Product;
import com.hm.badminton.entity.ProductCategory;
import com.hm.badminton.service.impl.EquipmentService;

import java.util.List;

public interface IEquipmentService {
    List<ProductCategory> categories(String sportCode);

    PageResult<Product> products(String sportCode, Long categoryId, String keyword, int page, int size);

    Product detail(Long id);

    void addCart(Long userId, EquipmentService.CartRequest request);

    List<CartItem> cart(Long userId);

    void removeCart(Long userId, Long itemId);

    Long createOrder(Long userId, EquipmentService.CreateOrderRequest request);

    void pay(Long userId, Long orderId);

    List<OrderSummary> orders(Long userId);

    Long createProduct(EquipmentService.ProductCreateRequest request);
}
