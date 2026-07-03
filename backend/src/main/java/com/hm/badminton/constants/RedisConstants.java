package com.hm.badminton.constants;

public final class RedisConstants {

    private RedisConstants() {
    }

    public static final String LOGIN_USER_KEY = "login:token:";
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final String BLOOM_USER_PHONE_KEY = "bf:user:phone";
    public static final String BLOOM_USER_EMAIL_KEY = "bf:user:email";
    public static final String BLOOM_USER_USERNAME_KEY = "bf:user:username";
    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String SECKILL_USER_KEY = "seckill:users:";
    public static final String SECKILL_ORDER_LOCK_KEY = "lock:seckill:order:";
    public static final String SECKILL_ACTIVITY_KEY = "seckill:activity:";
    public static final String FOLLOW_KEY = "follows:";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
}

