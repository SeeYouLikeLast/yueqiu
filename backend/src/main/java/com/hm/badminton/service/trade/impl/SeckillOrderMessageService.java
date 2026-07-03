package com.hm.badminton.service.trade.impl;

import com.hm.badminton.common.BusinessException;
import com.hm.badminton.constants.TradeType;
import com.hm.badminton.dto.SeckillOrderMessage;
import com.hm.badminton.mapper.trade.SeckillMapper;
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
        int type = TradeType.require(message.getType());
        Integer exists = type == TradeType.VENUE
                ? seckillMapper.countUserVenueOrder(message.getUserId(), message.getActivityId())
                : seckillMapper.countUserEquipmentOrder(message.getUserId(), message.getActivityId());
        if (exists != null && exists > 0) {
            return;
        }
        int updated = type == TradeType.VENUE
                ? seckillMapper.deductVenueActivityStock(message.getActivityId())
                : seckillMapper.deductEquipmentActivityStock(message.getActivityId());
        if (updated == 0) {
            throw new BusinessException("秒杀库存已抢完");
        }
        if (type == TradeType.VENUE) {
            seckillMapper.insertVenueOrder(message.getOrderId(), message.getActivityId(), message.getProductId(), message.getUserId(), message.getAmount());
        } else {
            seckillMapper.insertEquipmentOrder(message.getOrderId(), message.getActivityId(), message.getProductId(), message.getUserId(), message.getAmount());
            seckillMapper.deductEquipmentStockLenient(message.getProductId());
        }
    }
}
