package com.hello.pgproxy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
public class TaskConsumerService {
    private final PaymentFlowManager paymentFlowManager;

    // Simple loop thead
    private final ExecutorService eventLoopExecutor = Executors.newSingleThreadExecutor();

    @EventListener(ApplicationReadyEvent.class)
    public void startProcessingLoop() {
        eventLoopExecutor.execute(() -> {
            while (true) {
                try {
                    paymentFlowManager.processNext();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }
}
