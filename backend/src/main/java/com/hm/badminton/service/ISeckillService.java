package com.hm.badminton.service;

import com.hm.badminton.entity.SeckillActivity;
import com.hm.badminton.entity.SeckillOrder;

import java.util.List;
import java.util.Map;

public interface ISeckillService {
    List<SeckillActivity> list(Integer type, String sportCode, Long categoryId);

    Long submit(Long userId, Integer type, Long activityId);

    SeckillOrder order(Long userId, Integer type, Long orderId);

    List<SeckillOrder> myOrders(Long userId, Integer type);

    Map<String, Object> preload();
}
