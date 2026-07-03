package com.hm.badminton.service.trade;

import com.hm.badminton.common.PageResult;
import com.hm.badminton.entity.VenueOrder;
import com.hm.badminton.entity.VenueItem;
import com.hm.badminton.entity.VenueInventory;
import com.hm.badminton.entity.VenueCartItem;
import com.hm.badminton.service.trade.impl.VenueItemService;

import java.time.LocalDate;
import java.util.List;

public interface IVenueItemService {
    List<VenueItem> items(String sportCode, String amapPlaceId, Long venueId, Integer placeRank, int limit);

    PageResult<VenueItem> saleItems(String sportCode, String productType, String keyword, int page, int size);

    VenueItem detail(Long itemId);

    List<VenueInventory> inventories(Long itemId, LocalDate date);

    void addCart(Long userId, VenueItemService.OrderCreateRequest request);

    List<VenueCartItem> cart(Long userId);

    void removeCart(Long userId, Long itemId);

    void clearCart(Long userId);

    VenueOrder createOrder(Long userId, VenueItemService.OrderCreateRequest request);

    VenueOrder pay(Long userId, Long orderId);

    VenueOrder order(Long userId, Long orderId);

    List<VenueOrder> myOrders(Long userId);
}


