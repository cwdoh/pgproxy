package com.hello.pgproxy.service.backpressure;

import com.hello.pgproxy.configuration.ConcurrencyProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SimpleBackpressureHandlerImplTest {
    @Mock
    private ConcurrencyProperties concurrencyProperties;

    @InjectMocks
    private SimpleBackpressureHandlerImpl handler;

    private static final int MAX_CONCURRENCY = 100;

    @Test
    @DisplayName("Handler name should be 'simple'")
    void getHandlerName_shouldReturnSimple() {
        // Verify that the handler reports the correct name
        assertEquals("simple", handler.getHandlerName(), "Handler name must be 'simple'.");
    }

    @Test
    @DisplayName("Scale down should correctly reduce concurrency by 5 percent")
    void getScaleDownConcurrency_shouldReduceByFivePercent() {
        // Current concurrency is 50. Expected: 50 * 0.95 = 47.5, Math.max(1, 47) = 47
        int currentConcurrency = 50;
        int expected = 47;

        int result = handler.getScaleDownConcurrency(currentConcurrency);

        assertEquals(expected, result, "The concurrency should be reduced by 5% (50 -> 47).");
    }

    @Test
    @DisplayName("Scale down result must be at least 1")
    void getScaleDownConcurrency_shouldBeAtLeastOne() {
        // Current concurrency is 1. Expected: 1 * 0.95 = 0.95, Math.max(1, 0) = 1
        int currentConcurrency = 1;
        int expected = 1;

        int result = handler.getScaleDownConcurrency(currentConcurrency);

        assertEquals(expected, result, "The concurrency should not go below 1.");
    }

    // --- Scale Up Tests ---

    @Test
    @DisplayName("Scale up should increase concurrency by exactly 1")
    void getScaleUpConcurrency_shouldIncreaseByOne() {
        setupMaxConcurrency();

        int initialConcurrency = 50;

        // Expected increase: 50 + 1 = 51 (since 51 < MAX_CONCURRENCY)
        int expected = 51;
        int actual = handler.getScaleUpConcurrency(initialConcurrency);

        // Verify the step-by-step increase mechanism
        assertEquals(expected, actual, "Scale up should increase the limit by 1.");
    }

    @Test
    @DisplayName("Scale up should be capped by the maximum limit")
    void getScaleUpConcurrency_shouldNotExceedMax() {
        setupMaxConcurrency();

        // Expected: min(100 + 1, 100) = 100. It should be capped at the max.
        int actual = handler.getScaleUpConcurrency(MAX_CONCURRENCY);

        // Verify the limit cap
        assertEquals(MAX_CONCURRENCY, actual, "Scale up must not exceed the defined MAX concurrency limit.");
    }

    @Test
    @DisplayName("Scale up near max should be capped")
    void getScaleUpConcurrency_shouldBeCappedWhenNearMax() {
        setupMaxConcurrency();

        int initialConcurrency = MAX_CONCURRENCY - 1; // Start at 99

        // Expected: min(99 + 1, 100) = 100.
        int actual = handler.getScaleUpConcurrency(initialConcurrency);

        // Verify that it hits the maximum limit exactly
        assertEquals(MAX_CONCURRENCY, actual, "Scale up should reach the MAX limit but not exceed it.");
    }

    private void setupMaxConcurrency() {
        // Define the max concurrency limit for the mocked properties
        when(concurrencyProperties.getMax()).thenReturn(MAX_CONCURRENCY);
    }
}
