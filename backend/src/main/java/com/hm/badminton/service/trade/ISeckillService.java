package com.hm.badminton.service.trade;

import com.hm.badminton.entity.SeckillActivity;
import com.hm.badminton.entity.SeckillOrder;

import java.util.List;

public interface ISeckillService {
    List<SeckillActivity> list(Integer type, String sportCode, Long categoryId);

    Long submit(Long userId, Integer type, Long activityId);

    List<SeckillOrder> myOrders(Long userId, Integer type);
}


