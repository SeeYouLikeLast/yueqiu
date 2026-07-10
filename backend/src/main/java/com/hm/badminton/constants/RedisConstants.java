package com.hm.badminton.constants;

import java.time.Duration;

public final class RedisConstants {

    private RedisConstants() {
    }

    public static final String LOGIN_USER_KEY = "login:token:";
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final String LOGIN_EMAIL_CODE_KEY = "login:code:email:";
    public static final String LOGIN_EMAIL_CODE_COOLDOWN_KEY = "login:code:email:cooldown:";
    public static final String LOGIN_EMAIL_CODE_LIMIT_KEY = "login:code:email:limit:";
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
    public static final String AMAP_REGEOCODE_KEY = "amap:regeo:";
    public static final String SOCIAL_PLAYERS_KEY = "social:players:";
    public static final String SOCIAL_ACTIVITIES_KEY = "social:activities:";
    public static final String SPORT_LIST_KEY = "catalog:sports";
    public static final String CACHE_REBUILD_LOCK_KEY = "lock:cache:rebuild:";
    public static final String FOLLOW_KEY = "follows:";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String BLOG_OUTBOX_KEY = "blog:outbox:";
    public static final String AGENT_MEMORY_KEY = "agent:memory:";
    public static final String AGENT_RATE_USER_KEY = "agent:rate:user:";
    public static final String AGENT_RATE_IP_KEY = "agent:rate:ip:";
    public static final String CACHE_NULL_VALUE = "__NULL__";

    public static final Duration CACHE_NULL_TTL = Duration.ofMinutes(2);
    public static final Duration CACHE_DETAIL_TTL = Duration.ofMinutes(30);
    public static final Duration AMAP_REGEOCODE_TTL = Duration.ofMinutes(30);
    public static final Duration SOCIAL_LIST_TTL = Duration.ofMinutes(1);
    public static final Duration SPORT_LIST_TTL = Duration.ofHours(24);
    public static final Duration AGENT_MEMORY_TTL = Duration.ofMinutes(60);
    public static final Duration AGENT_RATE_TTL = Duration.ofMinutes(1);
    public static final Duration CACHE_LOGICAL_TTL = Duration.ofMinutes(30);
    public static final Duration CACHE_REBUILD_LOCK_TTL = Duration.ofSeconds(10);
    public static final long CACHE_DETAIL_JITTER_SECONDS = Duration.ofMinutes(5).toSeconds();
}
