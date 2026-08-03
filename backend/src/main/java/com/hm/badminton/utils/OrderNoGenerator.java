package com.hm.badminton.utils;

import java.util.UUID;

/** Generates opaque public order numbers while database relations continue using bigint ids. */
public final class OrderNoGenerator {

    private OrderNoGenerator() {
    }

    public static String next() {
        return UUID.randomUUID().toString();
    }
}
