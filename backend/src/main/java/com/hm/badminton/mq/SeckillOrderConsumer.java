package com.hm.badminton.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.badminton.common.BusinessException;
import com.hm.badminton.constants.MqConstants;
import com.hm.badminton.dto.SeckillOrderMessage;
import com.hm.badminton.service.trade.impl.SeckillOrderMessageService;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(
        topic = MqConstants.SECKILL_ORDER_TOPIC,
        consumerGroup = MqConstants.SECKILL_ORDER_CONSUMER_GROUP,
        consumeMode = ConsumeMode.CONCURRENTLY,
        messageModel = MessageModel.CLUSTERING
)
public class SeckillOrderConsumer implements RocketMQListener<String> {

    private static final Logger log = LoggerFactory.getLogger(SeckillOrderConsumer.class);

    private final SeckillOrderMessageService orderMessageService;
    private final ObjectMapper objectMapper;

    public SeckillOrderConsumer(SeckillOrderMessageService orderMessageService, ObjectMapper objectMapper) {
        this.orderMessageService = orderMessageService;
        this.objectMapper = objectMapper;
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
        }
    }
}
