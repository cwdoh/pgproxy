package com.hello.pgproxy.service;

import com.hello.pgproxy.configuration.ConcurrencyProperties;
import com.hello.pgproxy.model.PrioritizedTask;
import com.hello.pgproxy.service.backpressure.BackpressureHandler;
import com.hello.pgproxy.service.backpressure.BackpressureIntervalControl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BackpressurePolicyManagerTest {
    @Mock
    private ConcurrencyProperties concurrencyProperties;
    @Mock
    private List<BackpressureHandler> backpressureHandlers;
    @Mock
    private PriorityTaskQueueService priorityTaskQueueService;
    @Mock
    private BackpressureIntervalControl backpressureIntervalControl;
    @Mock
    private ConcurrencyLockObject concurrencyLockObject;

    @InjectMocks
    private BackpressurePolicyManager backpressurePolicyManager;

    // Mock BackpressureHandlers
    @Mock
    private BackpressureHandler mockHandlerA;
    @Mock
    private BackpressureHandler mockHandlerB;

    private final int INITIAL_LIMIT = 10;
    private final int MAX_LIMIT = 100;
    private final long SCALE_UP_INTERVAL = 5000L;
    private final long SCALE_DOWN_INTERVAL = 1000L;
    private final long UNPAUSE_DELAY = 50L;

    private void setupConcurrencyProperties() {
        lenient().when(concurrencyProperties.getStart()).thenReturn(INITIAL_LIMIT);
        lenient().when(concurrencyProperties.getMax()).thenReturn(MAX_LIMIT);
        lenient().when(concurrencyProperties.getScaleUpInterval()).thenReturn(SCALE_UP_INTERVAL);
        lenient().when(concurrencyProperties.getScaleDownInterval()).thenReturn(SCALE_DOWN_INTERVAL);
        lenient().when(concurrencyProperties.getUnpauseDelay()).thenReturn(UNPAUSE_DELAY);
        when(concurrencyProperties.getBackpressureHandler()).thenReturn("HandlerA");
    }

    private void setupHandlers() {
        // Handler Mocking
        when(mockHandlerA.getHandlerName()).thenReturn("HandlerA");

        // mock Scale Up/Down
        lenient().when(mockHandlerA.getScaleUpConcurrency(anyInt())).thenAnswer(
                invocation -> invocation.<Integer>getArgument(0) + 10);
        lenient().when(mockHandlerA.getScaleDownConcurrency(anyInt())).thenAnswer(
invocation -> invocation.<Integer>getArgument(0) - 5);

        // unused handler
        lenient().when(mockHandlerB.getHandlerName()).thenReturn("HandlerB");

        // Mock List of Handlers
        List<BackpressureHandler> handlers = Arrays.asList(mockHandlerA, mockHandlerB);
        when(backpressureHandlers.stream()).thenReturn(handlers.stream());
    }

    @BeforeEach
    void setUp() throws Exception {
        setupConcurrencyProperties();
        setupHandlers();

        backpressurePolicyManager.init();
    }

    @Test
    @DisplayName("Initial state verification (init and getInitialConcurrencyLimit)")
    void testInitAndInitialConcurrencyLimit() {
        final AtomicInteger concurrencyLimit = new AtomicInteger(INITIAL_LIMIT);
        when(backpressureIntervalControl.hasConcurrencyModifiableTimePassed(anyLong())).thenReturn(true);

        // Assert initial limit
        assertEquals(INITIAL_LIMIT, backpressurePolicyManager.getInitialConcurrencyLimit(), "Initial limit should come from properties.");

        // Assert handler selection
        backpressurePolicyManager.increaseConcurrencyLimit(concurrencyLimit);

        verify(mockHandlerA, times(1)).getScaleUpConcurrency(INITIAL_LIMIT);
        verify(mockHandlerB, never()).getScaleUpConcurrency(anyInt());
    }

    @Nested
    @DisplayName("Concurrency Limit Increase (Scale Up)")
    class ScaleUpTests {
        @Test
        @DisplayName("Should scale up when conditions are met")
        void testIncreaseConcurrencyLimit_ScaleUpSuccess() {
            final AtomicInteger currentConcurrencyLimit = new AtomicInteger(20);

            // Arrange: Time passed condition met
            when(backpressureIntervalControl.hasConcurrencyModifiableTimePassed(SCALE_UP_INTERVAL))
                    .thenReturn(true);

            // Act
            backpressurePolicyManager.increaseConcurrencyLimit(currentConcurrencyLimit);

            // Assert
            verify(mockHandlerA, times(1)).getScaleUpConcurrency(20);
            assertEquals(30, currentConcurrencyLimit.get(), "Limit should increase by 10 (20 -> 30).");
            verify(backpressureIntervalControl, times(1)).resetTimestamp();
            verifyNoMoreInteractions(mockHandlerA);
        }

        @Test
        @DisplayName("Should NOT scale up if time interval has not passed")
        void testIncreaseConcurrencyLimit_SkipDueToTimeInterval() {
            final AtomicInteger currentConcurrencyLimit = new AtomicInteger(50);

            // Arrange: Time condition NOT met
            when(backpressureIntervalControl.hasConcurrencyModifiableTimePassed(SCALE_UP_INTERVAL))
                    .thenReturn(false);

            // Act
            backpressurePolicyManager.increaseConcurrencyLimit(currentConcurrencyLimit);

            // Assert
            verify(mockHandlerA, never()).getScaleUpConcurrency(anyInt());
            assertEquals(50, currentConcurrencyLimit.get(), "Limit should remain unchanged.");
            verify(backpressureIntervalControl, never()).resetTimestamp();
        }

        @Test
        @DisplayName("Should NOT scale up if concurrency limit is at MAX")
        void testIncreaseConcurrencyLimit_SkipDueToMaxLimit() {
            final AtomicInteger currentConcurrencyLimit = new AtomicInteger(MAX_LIMIT);

            // Act
            backpressurePolicyManager.increaseConcurrencyLimit(currentConcurrencyLimit);

            // Assert
            verify(mockHandlerA, never()).getScaleUpConcurrency(anyInt());
            assertEquals(MAX_LIMIT, currentConcurrencyLimit.get(), "Limit should not exceed MAX_LIMIT.");
            verify(backpressureIntervalControl, never()).resetTimestamp();
        }
    }

    @Nested
    @DisplayName("Backpressure Handling (Scale Down & Pause)")
    class HandleBackpressureTests {
        private PrioritizedTask failedTask;
        private AtomicInteger currentConcurrencyLimit = new AtomicInteger(20);

        @BeforeEach
        void setup() {
            failedTask = mock(PrioritizedTask.class);
            currentConcurrencyLimit.set(50);
        }

        @Test
        @DisplayName("Should scale down, requeue, and pause when time condition met")
        void testHandleBackpressure_ScaleDownSuccess() throws InterruptedException {
            // Arrange: Time passed condition met
            when(backpressureIntervalControl.hasConcurrencyModifiableTimePassed(SCALE_DOWN_INTERVAL)).thenReturn(true);

            // Act
            backpressurePolicyManager.handleBackpressure(failedTask, currentConcurrencyLimit);

            // Assert Scale Down
            verify(mockHandlerA, times(1)).getScaleDownConcurrency(50);
            assertEquals(45, currentConcurrencyLimit.get(), "Limit should decrease by 5 (50 -> 45).");
            verify(backpressureIntervalControl, times(1)).resetTimestamp();

            // Assert Requeue and Pause
            verify(priorityTaskQueueService, times(1)).requeue(failedTask);
            assertTrue(backpressurePolicyManager.isPaused(), "Manager should be paused immediately.");

            // Assert Unpause (Asynchronous check)
            TimeUnit.MILLISECONDS.sleep(UNPAUSE_DELAY + 50); // Wait for delayedExecutor to finish
            assertFalse(backpressurePolicyManager.isPaused(), "Manager should be unpaused after delay.");
        }

        @Test
        @DisplayName("Should NOT scale down but still requeue and pause if time interval has not passed")
        void testHandleBackpressure_SkipScaleDown() throws InterruptedException {
            // Arrange: Time condition NOT met
            when(backpressureIntervalControl.hasConcurrencyModifiableTimePassed(SCALE_DOWN_INTERVAL)).thenReturn(false);

            // Act
            backpressurePolicyManager.handleBackpressure(failedTask, currentConcurrencyLimit);

            // Assert Scale Down Skip
            verify(mockHandlerA, never()).getScaleDownConcurrency(anyInt());
            assertEquals(50, currentConcurrencyLimit.get(), "Limit should remain unchanged.");
            verify(backpressureIntervalControl, never()).resetTimestamp();

            // Assert Requeue and Pause
            verify(priorityTaskQueueService, times(1)).requeue(failedTask);
            assertTrue(backpressurePolicyManager.isPaused(), "Manager should be paused immediately.");

            // Assert Unpause (Asynchronous check)
            TimeUnit.MILLISECONDS.sleep(UNPAUSE_DELAY + 50);
            assertFalse(backpressurePolicyManager.isPaused(), "Manager should be unpaused after delay.");
        }
    }
}
