package com.hello.pgproxy.service;

import com.hello.pgproxy.client.BackendApiClient;
import com.hello.pgproxy.configuration.ConcurrencyProperties;
import com.hello.pgproxy.model.BackendRequest;
import com.hello.pgproxy.model.ClientRequest;
import com.hello.pgproxy.model.PrioritizedTask;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.Executors.newSingleThreadExecutor;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentProxyService {
    private final BackendApiClient backendApiClient;
    private final VerificationStrategy verificationStrategy;
    private final ConcurrencyProperties  concurrencyProperties;

    private final PriorityBlockingQueue<PrioritizedTask> queue = new PriorityBlockingQueue<>();
    private final ExecutorService workerPool = Executors.newCachedThreadPool();
    private final AtomicInteger activeRequests = new AtomicInteger(0);

    private int currentConcurrencyLimit = 16;
    private volatile boolean isPaused = false;
    private Instant concurrencyChangeTimestamp = Instant.now();

    @PostConstruct
    public void init() {
        currentConcurrencyLimit = concurrencyProperties.getStart();
    }

    public void enqueue(ClientRequest request, DeferredResult<ResponseEntity<?>> deferredResult) {
        final Long verification = verificationStrategy.calculate(request);

        queue.add(new PrioritizedTask(request, verification, deferredResult));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startProcessingLoop() {
        newSingleThreadExecutor().execute(() -> {
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
        if (isPaused || activeRequests.get() >= currentConcurrencyLimit || queue.isEmpty()) {
            Thread.sleep(concurrencyProperties.getBusyWaitInterval());
            return;
        }

        PrioritizedTask task = queue.take();

        activeRequests.incrementAndGet();
        workerPool.submit(() -> executeTask(task));
    }

    private void executeTask(PrioritizedTask task) {
        try {
            final ClientRequest request = task.getRequest();
            final BackendRequest body = BackendRequest.builder()
                    .id(request.getId())
                    .amount_cents(request.getAmount_cents())
                    .verification(task.getVerification())
                    .build();

            final ResponseEntity<String> response = backendApiClient.postForEntity(body);

            // If backend is healthy, increase capacity
            if (currentConcurrencyLimit < concurrencyProperties.getMax() && hasConcurrencyModifiableTimePassed()) {
                currentConcurrencyLimit = Math.min(currentConcurrencyLimit + 1, concurrencyProperties.getMax());
                concurrencyChangeTimestamp = Instant.now();
            }

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
        }
    }

    private synchronized void handleBackpressure(PrioritizedTask failedTask) {
        int old = currentConcurrencyLimit;
        currentConcurrencyLimit = Math.max(1, currentConcurrencyLimit / 2);
        concurrencyChangeTimestamp = Instant.now();

        log.info("Backpressure control: Got 503 from backend. Concurrency {} -> {}", old, currentConcurrencyLimit);

        // Re-queue the failed task
        queue.add(failedTask);

        if (!isPaused) {
            isPaused = true;

            CompletableFuture
                    .delayedExecutor(concurrencyProperties.getUnpauseDelay(), TimeUnit.MILLISECONDS)
                    .execute(() -> isPaused = false);
        }
    }

    private boolean hasConcurrencyModifiableTimePassed() {
        Instant targetTime = concurrencyChangeTimestamp.plusMillis(concurrencyProperties.getModifiableInterval());

        return !Instant.now().isBefore(targetTime);
    }
}
