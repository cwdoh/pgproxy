package com.hello.pgproxy.service;

import com.hello.pgproxy.configuration.ConcurrencyProperties;
import com.hello.pgproxy.model.PrioritizedTask;
import com.hello.pgproxy.service.backpressure.BackpressureHandler;
import com.hello.pgproxy.service.backpressure.BackpressureIntervalControl;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class BackpressurePolicyManager {
    private final ConcurrencyProperties concurrencyProperties;
    private final List<BackpressureHandler> backpressureHandlers;
    private final PriorityTaskQueueService priorityTaskQueueService; // Dependency for requeue
    private final BackpressureIntervalControl backpressureIntervalControl;
    private final ConcurrencyLockObject concurrencyLockObject;

    @Getter
    private volatile boolean isPaused = false;

    // Concurrency is managed externally (by the FlowManager) but the logic is here
    private BackpressureHandler activeBackpressureHandler;

    @PostConstruct
    public void init() {
        activeBackpressureHandler = getActiveBackpressureHandler();
    }

    public int getInitialConcurrencyLimit() {
        return concurrencyProperties.getStart();
    }

    public synchronized void increaseConcurrencyLimit(AtomicInteger currentConcurrencyLimit) {
        int concurrencyLimit = currentConcurrencyLimit.get();
        if (concurrencyLimit < concurrencyProperties.getMax()
                && backpressureIntervalControl.hasConcurrencyModifiableTimePassed(concurrencyProperties.getScaleUpInterval())
        ) {
            int newConcurrency = activeBackpressureHandler.getScaleUpConcurrency(concurrencyLimit);
            currentConcurrencyLimit.set(newConcurrency);
            backpressureIntervalControl.resetTimestamp();

            log.info("Backpressure control: scale up concurrency {} -> {}", concurrencyLimit, newConcurrency);
        }
    }

    public synchronized void handleBackpressure(PrioritizedTask failedTask, AtomicInteger currentConcurrencyLimit) {
        if (backpressureIntervalControl.hasConcurrencyModifiableTimePassed(concurrencyProperties.getScaleDownInterval())) {
            int old = currentConcurrencyLimit.get();
            int newConcurrency = activeBackpressureHandler.getScaleDownConcurrency(old);
            currentConcurrencyLimit.set(newConcurrency);
            backpressureIntervalControl.resetTimestamp();

            log.info("Backpressure control: scale down concurrency {} -> {}", old, newConcurrency);
        }

        // Re-queue the failed task using the dedicated queue service
        priorityTaskQueueService.requeue(failedTask);

        if (!isPaused) {
            isPaused = true;

            CompletableFuture
                    .delayedExecutor(concurrencyProperties.getUnpauseDelay(), TimeUnit.MILLISECONDS)
                    .execute(() -> {
                        isPaused = false;
                        // Notify the monitor in the FlowManager to resume processing
                        synchronized (concurrencyLockObject) {
                            concurrencyLockObject.notify();
                        }
                    });
        }
    }

    private BackpressureHandler getActiveBackpressureHandler() {
        final String handlerName = concurrencyProperties.getBackpressureHandler();

        return backpressureHandlers.stream()
                .filter(handler -> handlerName.equals(handler.getHandlerName()))
                .findFirst()
                .orElseThrow();
    }
}
