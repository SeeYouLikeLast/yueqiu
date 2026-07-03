package com.hm.badminton.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.badminton.common.BusinessException;
import com.hm.badminton.constants.MqConstants;
import com.hm.badminton.constants.RedisConstants;
import com.hm.badminton.dto.SeckillOrderMessage;
import com.hm.badminton.service.trade.impl.SeckillOrderMessageService;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(
        topic = MqConstants.SECKILL_ORDER_TOPIC,
        consumerGroup = MqConstants.SECKILL_ORDER_CONSUMER_GROUP,
        consumeMode = ConsumeMode.CONCURRENTLY,
        messageModel = MessageModel.CLUSTERING,
        maxReconsumeTimes = MqConstants.SECKILL_ORDER_MAX_RECONSUME_TIMES
)
public class SeckillOrderConsumer implements RocketMQListener<String> {

    private static final Logger log = LoggerFactory.getLogger(SeckillOrderConsumer.class);

    private final SeckillOrderMessageService orderMessageService;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    public SeckillOrderConsumer(SeckillOrderMessageService orderMessageService,
                                ObjectMapper objectMapper,
                                StringRedisTemplate redisTemplate) {
        this.orderMessageService = orderMessageService;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void onMessage(String payload) {
        SeckillOrderMessage message;
        try {
            message = objectMapper.readValue(payload, SeckillOrderMessage.class);
        } catch (Exception e) {
            log.warn("秒杀订单消息反序列化失败, payload={}", payload, e);
            return;
        }

        try {
            orderMessageService.createOrder(message);
            log.info("秒杀订单异步创建成功, orderId={}", message.getOrderId());
        } catch (DuplicateKeyException e) {
            log.info("秒杀订单重复消息已忽略, userId={}, activityId={}", message.getUserId(), message.getActivityId());
        } catch (BusinessException e) {
            log.warn("秒杀订单异步创建失败, orderId={}, reason={}", message.getOrderId(), e.getMessage());
            rollbackRedisPreDeduct(message);
        }
    }

    private void rollbackRedisPreDeduct(SeckillOrderMessage message) {
        try {
            String stockKey = RedisConstants.SECKILL_STOCK_KEY + message.getType() + ":" + message.getActivityId();
            String userKey = RedisConstants.SECKILL_USER_KEY + message.getType() + ":" + message.getActivityId();
            redisTemplate.opsForValue().increment(stockKey);
            redisTemplate.opsForSet().remove(userKey, String.valueOf(message.getUserId()));
            log.info("秒杀订单补偿回滚成功, orderId={}, userId={}, activityId={}",
                    message.getOrderId(), message.getUserId(), message.getActivityId());
        } catch (Exception e) {
            log.error("秒杀订单补偿回滚失败, orderId={}, userId={}, activityId={}, 需要人工处理",
                    message.getOrderId(), message.getUserId(), message.getActivityId(), e);
        }
    }
}
