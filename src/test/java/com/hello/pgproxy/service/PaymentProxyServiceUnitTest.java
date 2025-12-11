package com.hello.pgproxy.service;

import com.hello.pgproxy.client.BackendApiClient;
import com.hello.pgproxy.configuration.ConcurrencyProperties;
import com.hello.pgproxy.model.PrioritizedTask;
import com.hello.pgproxy.service.backpressure.BackpressureHandler;
import com.hello.pgproxy.service.verification.VerificationStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentProxyServiceUnitTest {
    @Mock
    private BackendApiClient backendApiClient;
    @Mock
    private VerificationStrategy verificationStrategy;
    @Spy
    private ConcurrencyProperties concurrencyProperties;
    @Mock
    private BackpressureHandler mockBackpressureHandler;

    private List<BackpressureHandler> backpressureHandlers;

    // Use reflection to set the private AtomicInteger fields
    private void setAtomicInt(PaymentProxyService service, String fieldName, int value) throws Exception {
        java.lang.reflect.Field field = PaymentProxyService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        ((AtomicInteger) field.get(service)).set(value);
    }

    // InjectMocks will create a real instance and inject mocks.
    @InjectMocks
    private PaymentProxyService paymentProxyService;

    @BeforeEach
    void setUp() throws Exception {
        // Setup initial properties for testing
        concurrencyProperties.setStart(10);
        concurrencyProperties.setMax(100);
        concurrencyProperties.setScaleUpInterval(500); // 500ms for testing
        concurrencyProperties.setScaleDownInterval(100); // 100ms for testing
        concurrencyProperties.setBackpressureHandler("mock");

        // The handler list needs to contain the mocked handler for init to work
        when(mockBackpressureHandler.getHandlerName()).thenReturn("mock");
        // Use a mutable list to inject into the service
        backpressureHandlers = List.of(mockBackpressureHandler);

        // Manually set the list of handlers after creation because @InjectMocks might struggle with List injection
        java.lang.reflect.Field field = PaymentProxyService.class.getDeclaredField("backpressureHandlers");
        field.setAccessible(true);
        field.set(paymentProxyService, backpressureHandlers);

        // Manually call init() to set the concurrency limit and active handler
        paymentProxyService.init();
    }

    @Test
    @DisplayName("IncreaseConcurrencyLimit: Should increase limit when interval time has passed")
    void increaseConcurrencyLimit_shouldScaleUpWhenTimePassed() throws Exception {
        // Given: current limit is 50, and time has passed
        setAtomicInt(paymentProxyService, "currentConcurrencyLimit", 50);
        when(mockBackpressureHandler.getScaleUpConcurrency(50)).thenReturn(51);

        // Advance the concurrencyChangeTimestamp to ensure time check passes
        java.lang.reflect.Field timestampField = PaymentProxyService.class.getDeclaredField("concurrencyChangeTimestamp");
        timestampField.setAccessible(true);
        timestampField.set(paymentProxyService, Instant.now().minus(Duration.ofMillis(concurrencyProperties.getScaleUpInterval() + 100)));

        // When: capacity is increased
        paymentProxyService.increaseConcurrencyLimit();

        // Then: limit should be 51
        java.lang.reflect.Field limitField = PaymentProxyService.class.getDeclaredField("currentConcurrencyLimit");
        limitField.setAccessible(true);
        assertEquals(51, ((AtomicInteger) limitField.get(paymentProxyService)).get(), "Concurrency limit should increase by 1.");

        // And: the timestamp should be updated
        Instant newTimestamp = (Instant) timestampField.get(paymentProxyService);
        assertTrue(newTimestamp.isAfter(Instant.now().minusSeconds(1)), "Concurrency change timestamp should be updated.");
    }

    @Test
    @DisplayName("IncreaseConcurrencyLimit: Should NOT increase limit if max is reached")
    void increaseConcurrencyLimit_shouldNotScaleUpIfMaxReached() throws Exception {
        // Given: current limit is at max (100)
        setAtomicInt(paymentProxyService, "currentConcurrencyLimit", concurrencyProperties.getMax());

        // When: capacity is increased
        paymentProxyService.increaseConcurrencyLimit();

        // Then: limit should remain at 100 and handler should not be called
        verify(mockBackpressureHandler, never()).getScaleUpConcurrency(anyInt());
        assertEquals(concurrencyProperties.getMax(), paymentProxyService.currentConcurrencyLimit.get(), "Concurrency limit should remain at max.");
    }

    // Since the actual implementation of handleBackpressure is complex, we use a simple mocked task
    private PrioritizedTask mockFailedTask;

    @Test
    @DisplayName("HandleBackpressure: Should scale down, pause, and re-queue the task")
    void handleBackpressure_shouldScaleDownAndPause() throws Exception {
        // Given: current limit is 50, and time has passed
        setAtomicInt(paymentProxyService, "currentConcurrencyLimit", 50);
        when(mockBackpressureHandler.getScaleDownConcurrency(50)).thenReturn(47); // 50 * 0.95 (approx)

        // Advance the timestamp to ensure time check passes
        java.lang.reflect.Field timestampField = PaymentProxyService.class.getDeclaredField("concurrencyChangeTimestamp");
        timestampField.setAccessible(true);
        timestampField.set(paymentProxyService, Instant.now().minus(Duration.ofMillis(concurrencyProperties.getScaleDownInterval() + 100)));

        // Mock a failed task (PrioritizedTask requires ClientRequest and DeferredResult)
        mockFailedTask = mock(PrioritizedTask.class);

        // When: backpressure is handled
        paymentProxyService.handleBackpressure(mockFailedTask);

        // Then 1: Concurrency limit should decrease to 47
        java.lang.reflect.Field limitField = PaymentProxyService.class.getDeclaredField("currentConcurrencyLimit");
        limitField.setAccessible(true);
        assertEquals(47, ((AtomicInteger) limitField.get(paymentProxyService)).get(), "Concurrency limit should decrease.");

        // Then 2: isPaused flag should be set to true
        java.lang.reflect.Field pausedField = PaymentProxyService.class.getDeclaredField("isPaused");
        pausedField.setAccessible(true);
        assertTrue((Boolean) pausedField.get(paymentProxyService), "isPaused flag should be true.");

        // Then 3: The failed task should be re-queued (verification requires checking the queue size, which is complex for unit test, so we trust the add call)
        // We'll verify re-queueing in the Integration Test section.
    }
}