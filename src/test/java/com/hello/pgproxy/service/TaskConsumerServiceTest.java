package com.hello.pgproxy.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskConsumerServiceTest {

    @Mock
    private PaymentFlowManager paymentFlowManager;

    @InjectMocks
    private TaskConsumerService taskConsumerService;

    @Test
    @DisplayName("startProcessingLoop: Should initiate continuous calls to processNext")
    void startProcessingLoop_ShouldStartContinuousProcessing() throws Exception {
        final int EXPECT_COUNT = 50;
        final AtomicInteger counter = new AtomicInteger(EXPECT_COUNT);
        final Object lock = new Object();

        // processNext() mocked as counter for wait after EXPECT_COUNT
        doAnswer((Answer<Void>) invocationOnMock -> {
            int remainingCount = counter.decrementAndGet();

            if (remainingCount == 0) {
                lock.wait();
            }

            return null;
        }).when(paymentFlowManager).processNext();

        // Instead of ApplicationReadyEvent, just start loop
        taskConsumerService.startProcessingLoop();

        // Wait for lock
        Thread.sleep(500);

        // processNext should be called EXPECT_COUNT times
        verify(paymentFlowManager, times(EXPECT_COUNT)).processNext();
    }

    @Test
    @DisplayName("startProcessingLoop: Should terminate the loop if an InterruptedException occurs")
    void startProcessingLoop_ShouldHandleInterruptionGracefully() throws Exception {
        doThrow(InterruptedException.class).when(paymentFlowManager).processNext();

        // Instead of ApplicationReadyEvent, just start loop
        taskConsumerService.startProcessingLoop();

        // Wait for lock
        Thread.sleep(500);

        // processNext should never be called
        verify(paymentFlowManager, times(1)).processNext();
    }
}
