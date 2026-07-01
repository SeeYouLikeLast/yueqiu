package com.hm.badminton.service;

import com.hm.badminton.common.PageResult;
import com.hm.badminton.entity.VenueOrder;
import com.hm.badminton.entity.VenueProduct;
import com.hm.badminton.entity.VenueProductInventory;
import com.hm.badminton.service.impl.VenueProductService;

import java.time.LocalDate;
import java.util.List;

public interface IVenueProductService {
    List<VenueProduct> products(String sportCode, String amapPlaceId, Long venueId, Integer placeRank, int limit);

    PageResult<VenueProduct> saleProducts(String sportCode, String productType, String keyword, int page, int size);

    VenueProduct detail(Long productId);

    List<VenueProductInventory> inventories(Long productId, LocalDate date);

    VenueOrder createOrder(Long userId, VenueProductService.OrderCreateRequest request);

    VenueOrder quickPay(Long userId, Long productId, VenueProductService.VenueContextRequest request);

    VenueOrder pay(Long userId, Long orderId);

    VenueOrder order(Long userId, Long orderId);

    List<VenueOrder> myOrders(Long userId);
}
