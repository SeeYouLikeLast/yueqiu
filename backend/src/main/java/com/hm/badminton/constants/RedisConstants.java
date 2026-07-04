package com.hm.badminton.constants;

import java.time.Duration;

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
    public static final String EQUIPMENT_DETAIL_KEY = "equipment:";
    public static final String VENUE_ITEM_DETAIL_KEY = "venue:item:";
    public static final String USER_PROFILE_KEY = "user:profile:";
    public static final String BLOG_DETAIL_KEY = "blog:";
    public static final String FOLLOW_KEY = "follows:";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String CACHE_NULL_VALUE = "__NULL__";

    public static final Duration CACHE_NULL_TTL = Duration.ofMinutes(2);
    public static final Duration CACHE_DETAIL_TTL = Duration.ofMinutes(30);
    public static final long CACHE_DETAIL_JITTER_SECONDS = Duration.ofMinutes(5).toSeconds();
}
