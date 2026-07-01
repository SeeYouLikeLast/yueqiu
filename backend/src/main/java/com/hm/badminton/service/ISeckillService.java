package com.hm.badminton.service;

import com.hm.badminton.entity.SeckillActivity;
import com.hm.badminton.entity.SeckillOrder;

import java.util.List;
import java.util.Map;

public interface ISeckillService {
    List<SeckillActivity> list(String sportCode, Long categoryId);

    Long submit(Long userId, Long activityId);

    SeckillOrder order(Long userId, Long orderId);

    List<SeckillOrder> myOrders(Long userId);

    Map<String, Object> preload();
}
