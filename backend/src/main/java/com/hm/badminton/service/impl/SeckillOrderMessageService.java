package com.hm.badminton.service.impl;

import com.hm.badminton.common.BusinessException;
import com.hm.badminton.dto.SeckillOrderMessage;
import com.hm.badminton.mapper.SeckillMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeckillOrderMessageService {

    private final SeckillMapper seckillMapper;

    public SeckillOrderMessageService(SeckillMapper seckillMapper) {
        this.seckillMapper = seckillMapper;
    }

    @Transactional
    public void createOrder(SeckillOrderMessage message) {
        Integer exists = seckillMapper.countUserActivityOrder(message.userId(), message.activityId());
        if (exists != null && exists > 0) {
            return;
        }
        int updated = seckillMapper.deductActivityStock(message.activityId());
        if (updated == 0) {
            throw new BusinessException("秒杀库存已抢完");
        }
        seckillMapper.insertOrder(message.orderId(), message.activityId(), message.productId(), message.userId(), message.amount());
        seckillMapper.deductProductStockLenient(message.productId());
    }
}
