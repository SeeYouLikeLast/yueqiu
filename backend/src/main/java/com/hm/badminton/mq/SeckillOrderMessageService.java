package com.hm.badminton.mq;

import com.hm.badminton.common.BusinessException;
import com.hm.badminton.constants.RedisConstants;
import com.hm.badminton.constants.TradeType;
import com.hm.badminton.dto.SeckillOrderMessage;
import com.hm.badminton.mapper.trade.SeckillMapper;
import com.hm.badminton.utils.CacheClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 秒杀消息的事务落库服务。
 *
 * <p>MQ 消费和“未部署 MQ 的同步降级”都会调用这里，因此两条路径共享完全相同的库存与订单
 * 规则。Redis 预扣只负责入口速度，MySQL 条件扣减和唯一索引才是最终兜底。</p>
 */
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
        // 1. 相同用户、相同活动已存在订单时直接返回，使重复消息具备幂等性。
        int type = TradeType.require(message.getType());
        Integer exists = type == TradeType.VENUE
                ? seckillMapper.countUserVenueOrder(message.getUserId(), message.getActivityId())
                : seckillMapper.countUserEquipmentOrder(message.getUserId(), message.getActivityId());
        if (exists != null && exists > 0) {
            return;
        }
        // 2. 数据库再次条件扣秒杀库存；Redis 与数据库短暂不一致时也不会超卖。
        int updated = type == TradeType.VENUE
                ? seckillMapper.deductVenueActivityStock(message.getActivityId())
                : seckillMapper.deductEquipmentActivityStock(message.getActivityId());
        if (updated == 0) {
            throw new BusinessException("秒杀库存已抢完");
        }
        // 3. 按交易类型写入各自订单表；装备秒杀还同步减少普通装备实物库存。
        if (type == TradeType.VENUE) {
            seckillMapper.insertVenueOrder(message.getOrderId(), message.getActivityId(), message.getProductId(), message.getUserId(), message.getAmount());
        } else {
            seckillMapper.insertEquipmentOrder(message.getOrderId(), message.getActivityId(), message.getProductId(), message.getUserId(), message.getAmount());
            seckillMapper.deductEquipmentStockLenient(message.getProductId());
            cacheClient.delete(RedisConstants.EQUIPMENT_DETAIL_KEY + message.getProductId());
        }
    }
}
