package com.hello.pgproxy.service.backpressure;

import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class BackpressureIntervalControl {
    private Instant concurrencyChangeTimestamp = Instant.now();

    public boolean hasConcurrencyModifiableTimePassed(long givenInterval) {
        Instant targetTime = concurrencyChangeTimestamp.plusMillis(givenInterval);
        return Instant.now().isAfter(targetTime);
    }

    public void resetTimestamp() {
        concurrencyChangeTimestamp = Instant.now();
    }
}
