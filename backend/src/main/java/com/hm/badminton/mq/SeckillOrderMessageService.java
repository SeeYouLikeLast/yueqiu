package com.hm.badminton.mq;

import com.hm.badminton.common.BusinessException;
import com.hm.badminton.constants.RedisConstants;
import com.hm.badminton.constants.TradeType;
import com.hm.badminton.dto.SeckillOrderMessage;
import com.hm.badminton.mapper.trade.SeckillMapper;
import com.hm.badminton.utils.CacheClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeckillOrderMessageService {

    private final SeckillMapper seckillMapper;
    private final CacheClient cacheClient;

    public SeckillOrderMessageService(SeckillMapper seckillMapper, CacheClient cacheClient) {
        this.seckillMapper = seckillMapper;
        this.cacheClient = cacheClient;
    }

    // RocketMQ 消费端真正落库，数据库唯一索引继续兜底一人一单。
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
            cacheClient.delete(RedisConstants.EQUIPMENT_DETAIL_KEY + message.getProductId());
        }
    }
}
