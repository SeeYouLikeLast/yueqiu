package com.hm.badminton.utils;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

@Component
public class IdGenerator {

    private final AtomicInteger sequence = new AtomicInteger();

    public long nextId() {
        long millis = Instant.now().toEpochMilli();
        int seq = sequence.getAndIncrement() & 0x3ff;
        return (millis << 10) | seq;
    }
}


