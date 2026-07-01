package com.hm.badminton.utils;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

public final class RedisTtl {

    private RedisTtl() {
    }

    public static Duration withJitter(Duration base, long maxJitterSeconds) {
        if (base == null || maxJitterSeconds <= 0) {
            return base;
        }
        return base.plusSeconds(ThreadLocalRandom.current().nextLong(maxJitterSeconds + 1));
    }
}
