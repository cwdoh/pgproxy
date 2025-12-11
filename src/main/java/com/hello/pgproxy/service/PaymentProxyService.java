package com.hello.pgproxy.service;

import com.hello.pgproxy.client.BackendApiClient;
import com.hello.pgproxy.configuration.ConcurrencyProperties;
import com.hello.pgproxy.model.BackendRequest;
import com.hello.pgproxy.model.ClientRequest;
import com.hello.pgproxy.model.PrioritizedTask;
import com.hello.pgproxy.service.backpressure.BackpressureHandler;
import com.hello.pgproxy.service.verification.VerificationStrategy;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.context.request.async.DeferredResult;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service responsible for managing payment requests with a focus on revenue maximization.
 * Prioritization:
 * - Uses a {@link PriorityBlockingQueue} to order requests by {@link PrioritizedTask}
 * - This ensures that high-value transactions are processed first
 * Concurrency:
 * - Acts as a buffer between the client and the backend.
 * - This decoupling allows the proxy can handle traffic, effectively providing a backpressure strategy.
 * Trade-off:
 * - While enqueue() introduces a slight computational overhead, but this cost is negligible to achieve the goal.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentProxyService {
    private final BackendApiClient backendApiClient;
    private final ConcurrencyProperties  concurrencyProperties;
    private final List<BackpressureHandler> backpressureHandlers;
    private final PriorityTaskQueueService  priorityTaskQueueService;

    // Allocate the dedicate thread pool for I/O works
    private final ExecutorService backendWorkPool = Executors.newVirtualThreadPerTaskExecutor();
    // Simple loop thead
    private final ExecutorService eventLookExecutor = Executors.newSingleThreadExecutor();

    private final Object monitor = new Object();
    private final AtomicInteger activeRequests = new AtomicInteger(0);
    private final AtomicInteger currentConcurrencyLimit = new AtomicInteger(16);
    private volatile boolean isPaused = false;
    private Instant concurrencyChangeTimestamp = Instant.now();
    private BackpressureHandler activeBackpressureHandler;

    @PostConstruct
    public void init() {
        currentConcurrencyLimit.set(concurrencyProperties.getStart());
        activeBackpressureHandler = getActiveBackpressureHandler();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startProcessingLoop() {
        eventLookExecutor.execute(() -> {
            while (true) {
                try {
                    processNext();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    private void processNext() throws InterruptedException {
        synchronized (monitor) {
            while (isPaused || activeRequests.get() >= currentConcurrencyLimit.get()) {
                monitor.wait();
            }
        }

        PrioritizedTask task = priorityTaskQueueService.take();

        activeRequests.incrementAndGet();
        backendWorkPool.submit(() -> executeTask(task));
    }

    private void executeTask(PrioritizedTask task) {
        try {
            final ClientRequest request = task.getRequest();
            final BackendRequest body = BackendRequest.builder()
                    .id(request.getId())
                    .amount_cents(request.getAmount_cents())
                    .verification(task.getVerification())
                    .build();

            final ResponseEntity<?> response = backendApiClient.postForEntity(body);

            // If backend is healthy, increase capacity
            increaseConcurrencyLimit();

            task.getDeferredResponse().setResult(response);
        } catch (HttpServerErrorException.ServiceUnavailable e) {
            handleBackpressure(task);
        } catch (HttpClientErrorException e) {
            // Client Error (400) - Pass through to client
            task.getDeferredResponse().setResult(
                    ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString())
            );
        } catch (Exception e) {
            task.getDeferredResponse().setResult(
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
            );
        } finally {
            activeRequests.decrementAndGet();

            synchronized (monitor) {
                monitor.notify();
            }
        }
    }

    private synchronized void increaseConcurrencyLimit() {
        int concurrencyLimit = currentConcurrencyLimit.get();
        if (concurrencyLimit < concurrencyProperties.getMax()
                && hasConcurrencyModifiableTimePassed(concurrencyProperties.getScaleUpInterval())
        ) {
            int newConcurrency = activeBackpressureHandler.getScaleUpConcurrency(concurrencyLimit);
            currentConcurrencyLimit.set(newConcurrency);
            concurrencyChangeTimestamp = Instant.now();
        }
    }

    private synchronized void handleBackpressure(PrioritizedTask failedTask) {
        if (hasConcurrencyModifiableTimePassed(concurrencyProperties.getScaleDownInterval())) {
            int old = currentConcurrencyLimit.get();
            int newConcurrency = activeBackpressureHandler.getScaleDownConcurrency(old);
            currentConcurrencyLimit.set(newConcurrency);
            concurrencyChangeTimestamp = Instant.now();

            log.info("Backpressure control: Got 503 from backend. trying change concurrency {} -> {}", old, newConcurrency);
        }

        // Re-queue the failed task
        priorityTaskQueueService.requeue(failedTask);

        if (!isPaused) {
            isPaused = true;

            CompletableFuture
                    .delayedExecutor(concurrencyProperties.getUnpauseDelay(), TimeUnit.MILLISECONDS)
                    .execute(() -> {
                        isPaused = false;
                        synchronized (monitor) {
                            monitor.notify();
                        }
                    });
        }
    }

    private boolean hasConcurrencyModifiableTimePassed(long givenInterval) {
        Instant targetTime = concurrencyChangeTimestamp.plusMillis(givenInterval);

        return Instant.now().isAfter(targetTime);
    }

    private BackpressureHandler getActiveBackpressureHandler() {
        final String handlerName = concurrencyProperties.getBackpressureHandler();

        return backpressureHandlers.stream()
                .filter(handler -> handlerName.equals(handler.getHandlerName()))
                .findFirst()
                .orElseThrow();
    }
}
