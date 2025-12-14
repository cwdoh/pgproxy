package com.hello.pgproxy.service;

import com.hello.pgproxy.model.BackendExecutionStatus;
import com.hello.pgproxy.model.PrioritizedTask;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentFlowManager {
    private final BackpressurePolicyManager backpressurePolicyManager;
    private final PriorityTaskQueueService priorityTaskQueueService;
    private final PaymentExecutor paymentExecutor;
    private final ConcurrencyLockObject concurrencyLockObject;

    // I/O thread pool
    private final ExecutorService backendWorkPool = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicInteger activeRequests = new AtomicInteger(0);

    // Concurrency state variables
    private final AtomicInteger currentConcurrencyLimit = new AtomicInteger(0);

    @PostConstruct
    public void init() {
        // Initialization logic for the flow manager
        currentConcurrencyLimit.set(backpressurePolicyManager.getInitialConcurrencyLimit());
    }

    public void processNext() throws InterruptedException {
        synchronized (concurrencyLockObject) {
            // Wait logic uses the state managed in this class
            while (backpressurePolicyManager.isPaused() || activeRequests.get() >= currentConcurrencyLimit.get()) {
                concurrencyLockObject.wait();
            }
        }

        final PrioritizedTask task = priorityTaskQueueService.take();

        activeRequests.incrementAndGet();

        backendWorkPool.submit(() -> {
            try {
                // Delegate execution to the dedicated service
                final BackendExecutionStatus executionResult = paymentExecutor.execute(task);

                switch (executionResult) {
                    case COMPLETED -> backpressurePolicyManager.increaseConcurrencyLimit(currentConcurrencyLimit);
                    case BACKPRESSURE_CONTROL_NEEDED ->
                            backpressurePolicyManager.handleBackpressure(task, currentConcurrencyLimit);
                }
            } finally {
                // Ensure synchronization is handled after execution
                activeRequests.decrementAndGet();
                synchronized (concurrencyLockObject) {
                    concurrencyLockObject.notify();
                }
            }
        });
    }
}
