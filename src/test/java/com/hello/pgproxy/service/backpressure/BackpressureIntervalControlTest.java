package com.hello.pgproxy.service.backpressure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
public class BackpressureIntervalControlTest {
    private BackpressureIntervalControl control;
    
    private final long SHORT_INTERVAL_MS = 100L; // 100 milliseconds
    private final long LONG_INTERVAL_MS = 5000L; // 5 seconds

    @BeforeEach
    void setUp() {
        control = new BackpressureIntervalControl();

        // Ensure the timestamp is initialized to a recent value before each test
        control.resetTimestamp();
    }

    @Test
    @DisplayName("Should return TRUE when the required time interval has fully passed")
    void shouldReturnTrueWhenTimeIntervalHasPassed() throws InterruptedException {
        // GIVEN: Set the timestamp far enough in the past to ensure the interval passes.
        Instant past = Instant.now().minusMillis(SHORT_INTERVAL_MS + 50);
        ReflectionTestUtils.setField(control, "concurrencyChangeTimestamp", past);

        // Wait a short duration to ensure Instant.now() is definitely after the target time.
        TimeUnit.MILLISECONDS.sleep(20);

        // WHEN
        boolean result = control.hasConcurrencyModifiableTimePassed(SHORT_INTERVAL_MS);

        // THEN
        assertTrue(result, "The method should return true as the required interval has elapsed.");
    }

    @Test
    @DisplayName("Should return FALSE when the required time interval has NOT passed")
    void shouldReturnFalseWhenTimeIntervalHasNotPassed() {
        // GIVEN: Timestamp is reset to Instant.now() in setUp(), making it immediately recent.

        // WHEN
        // Check against a long interval right after reset.
        boolean result = control.hasConcurrencyModifiableTimePassed(LONG_INTERVAL_MS);

        // THEN
        assertFalse(result, "The method should return false as the long interval has not yet elapsed.");
    }

    @Test
    @DisplayName("Should return FALSE when the time is exWHENly equal to the target time")
    void shouldReturnFalseWhenTimeIsExWHENlyEqualToTarget() {
        // GIVEN: The logic uses `isAfter(targetTime)`, meaning equality should return false.
        // We simulate a scenario where the current time is exWHENly the target time by using a very small interval.
        long negligibleInterval = 1;

        // WHEN (Immediately after reset)
        boolean result = control.hasConcurrencyModifiableTimePassed(negligibleInterval);

        // THEN
        assertFalse(result, "The method should return false if the current time is not strictly after the target time.");
    }
}
