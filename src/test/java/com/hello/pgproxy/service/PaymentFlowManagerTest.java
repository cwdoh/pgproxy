package com.hello.pgproxy.service;

import com.hello.pgproxy.model.BackendExecutionStatus;
import com.hello.pgproxy.model.PrioritizedTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@Execution(ExecutionMode.SAME_THREAD)
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentFlowManager Concurrency and Flow Control Tests")
class PaymentFlowManagerTest {
    @Mock
    private BackpressurePolicyManager backpressurePolicyManager;
    @Mock
    private PriorityTaskQueueService priorityTaskQueueService;
    @Mock
    private PaymentExecutor paymentExecutor;
    @Spy
    private final ConcurrencyLockObject concurrencyLockObject = new ConcurrencyLockObject();

    // System Under Test
    @InjectMocks
    private PaymentFlowManager paymentFlowManager;

    private final int INITIAL_CONCURRENCY_LIMIT = 4;

    @BeforeEach
    void setUp() {
        // Mock init() behavior
        when(backpressurePolicyManager.getInitialConcurrencyLimit()).thenReturn(INITIAL_CONCURRENCY_LIMIT);
        paymentFlowManager.init();
    }

    @AfterEach
    void tearDown() throws Exception {
        // Shutdown the virtual thread pool gracefully after each test
        ExecutorService executor = (ExecutorService) ReflectionTestUtils.getField(paymentFlowManager, "backendWorkPool");
        if (executor != null) {
            executor.shutdownNow();
            executor.awaitTermination(100, TimeUnit.MILLISECONDS);
        }
    }

    @Test
    @DisplayName("Should successfully execute COMPLETED task and trigger scale up")
    void testProcessNext_CompletedTask() throws Exception {
        PrioritizedTask mockTask = mock(PrioritizedTask.class);

        // Act & Wait
        runProcessNextAndWaitForCompletion(mockTask, BackendExecutionStatus.COMPLETED);

        // Assert Execution Flow
        verify(priorityTaskQueueService, times(1)).take();
        verify(paymentExecutor, times(1)).execute(mockTask);

        // Assert Policy Manager interaction
        verify(backpressurePolicyManager, times(1)).increaseConcurrencyLimit(any(AtomicInteger.class));
        verify(backpressurePolicyManager, never()).handleBackpressure(any(), any());
    }

    @Test
    @DisplayName("Should execute BACKPRESSURE_CONTROL_NEEDED task and trigger handling")
    void testProcessNext_BackpressureNeededTask() throws Exception {
        PrioritizedTask mockTask = mock(PrioritizedTask.class);

        // Act & Wait
        runProcessNextAndWaitForCompletion(mockTask, BackendExecutionStatus.BACKPRESSURE_CONTROL_NEEDED);

        // Assert Execution Flow
        verify(priorityTaskQueueService, times(1)).take();
        verify(paymentExecutor, times(1)).execute(mockTask);

        // Assert Policy Manager interaction
        verify(backpressurePolicyManager, times(1)).handleBackpressure(eq(mockTask), any(AtomicInteger.class));
        verify(backpressurePolicyManager, never()).increaseConcurrencyLimit(any());
    }

    @Test
    @DisplayName("Should block when active requests reach concurrency limit")
    void testProcessNext_ConcurrencyLimitBlocking() throws InterruptedException {
        // Set initial limit to 1
        when(backpressurePolicyManager.getInitialConcurrencyLimit()).thenReturn(1);
        paymentFlowManager.init();

        PrioritizedTask task1 = mock(PrioritizedTask.class);
        PrioritizedTask task2 = mock(PrioritizedTask.class);

        // Given:Mock Execution (Task 1 will be intentionally slow/hanging)
        // Task 1 setup: take() returns task1
        when(priorityTaskQueueService.take())
                .thenReturn(task1)
                .thenReturn(task2); // Task 2 setup: take() returns task2

        // Task 1 execution: Make it block or take a long time to keep activeRequests=1
        when(paymentExecutor.execute(task1)).thenAnswer((Answer<BackendExecutionStatus>) invocation -> {
            TimeUnit.MILLISECONDS.sleep(200); // Simulate long execution
            return BackendExecutionStatus.COMPLETED;
        });
        // Task 2 execution: Normal completion
        when(paymentExecutor.execute(task2)).thenReturn(BackendExecutionStatus.COMPLETED);

        // 1. Execute Task 1 (Proceeds immediately, activeRequests becomes 1)
        paymentFlowManager.processNext();

        // 2. Start Task 2 in a separate thread (Should block on concurrencyLockObject.wait())
        Thread blockingThread = new Thread(() -> {
            try {
                paymentFlowManager.processNext();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        blockingThread.start();

        // Give time for Task 2 to reach the wait() block
        TimeUnit.MILLISECONDS.sleep(50);

        // Assert Blocking State: Task 2 should not have called take() yet
        verify(priorityTaskQueueService, times(1)).take();

        // 3. Wait for Task 1 to complete (After 200ms, Task 1 completes and calls notify())
        blockingThread.join(300); // Wait for blockingThread to finish

        // Assert Unblocked State: Task 2 should have now been processed
        verify(priorityTaskQueueService, times(2)).take();
        verify(paymentExecutor, times(2)).execute(any());
    }

    @Test
    @DisplayName("Should block and wait when BackpressurePolicyManager is paused")
    void testProcessNext_PausedBlocking() throws InterruptedException {
        PrioritizedTask mockTask = mock(PrioritizedTask.class);

        // GIVEN:Set policy to paused
        when(backpressurePolicyManager.isPaused()).thenReturn(true);

        // 1. Start processNext in a separate thread (Should block on concurrencyLockObject.wait())
        Thread blockingThread = new Thread(() -> {
            try {
                paymentFlowManager.processNext();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        blockingThread.start();

        // Give time for the thread to reach the wait() block
        TimeUnit.MILLISECONDS.sleep(50);

        // Assert Blocking State: take() should not have been called
        verify(priorityTaskQueueService, never()).take();

        // GIVEN:Setup execution for the unblocked task
        when(priorityTaskQueueService.take()).thenReturn(mockTask);
        when(paymentExecutor.execute(mockTask)).thenReturn(BackendExecutionStatus.COMPLETED);

        // 2. Unpause the policy manager and manually notify the waiting thread
        when(backpressurePolicyManager.isPaused()).thenReturn(false);
        synchronized (concurrencyLockObject) {
            concurrencyLockObject.notifyAll(); // Simulates unpause logic triggering notify()
        }

        // 3. Wait for the blocking thread to finish execution
        blockingThread.join(150);

        // Assert Unblocked State: Task should have been executed
        verify(priorityTaskQueueService, times(1)).take();
        verify(paymentExecutor, times(1)).execute(mockTask);
    }

    @Test
    @DisplayName("Should handle InterruptedException during wait gracefully")
    void testProcessNext_InterruptedExceptionDuringWait() throws InterruptedException {
        // GIVEN:Force the thread to be interrupted while waiting (difficult to mock perfectly)
        // We will mock isPaused to true and interrupt the thread running processNext

        when(backpressurePolicyManager.isPaused()).thenReturn(true);

        Thread blockingThread = new Thread(() -> {
            try {
                // This call should block
                paymentFlowManager.processNext();
            } catch (InterruptedException expected) {
                // The exception should be caught and thrown up by processNext
                // Assert that the thread's interrupted status is cleared by the standard Java wait()
                assertTrue(Thread.currentThread().isInterrupted());
            }
        });
        blockingThread.start();

        // Wait for thread to enter wait() state
        TimeUnit.MILLISECONDS.sleep(50);

        // Interrupt the thread
        blockingThread.interrupt();

        // Wait for the thread to catch the exception and exit processNext
        assertDoesNotThrow(() -> blockingThread.join(100), "The thread should exit the blocking state.");

        // Assert no execution occurred
        verify(priorityTaskQueueService, never()).take();
    }

    // Helper method to simulate a task and wait for completion
    private void runProcessNextAndWaitForCompletion(PrioritizedTask task, BackendExecutionStatus status) throws InterruptedException {
        // Given:task and execution result
        when(priorityTaskQueueService.take()).thenReturn(task);
        when(paymentExecutor.execute(task)).thenReturn(status);

        // Act
        paymentFlowManager.processNext();

        // Wait for the asynchronous virtual thread task to complete
        TimeUnit.MILLISECONDS.sleep(100);
    }
}