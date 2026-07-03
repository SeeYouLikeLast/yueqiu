package com.hm.badminton.constants;

public final class MqConstants {

    private MqConstants() {
    }

    public static final String SECKILL_ORDER_TOPIC = "hm-seckill-order";
    public static final String SECKILL_ORDER_CONSUMER_GROUP = "hm-seckill-order-consumer";
    public static final int SECKILL_ORDER_MAX_RECONSUME_TIMES = 3;
}

