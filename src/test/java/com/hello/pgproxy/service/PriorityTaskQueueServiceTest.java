package com.hello.pgproxy.service;

import com.hello.pgproxy.model.ClientRequest;
import com.hello.pgproxy.model.PrioritizedTask;
import com.hello.pgproxy.service.verification.VerificationStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.DeferredResult;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PriorityTaskQueueServiceTest {
    @Mock
    private VerificationStrategy mockVerificationStrategy;

    @InjectMocks
    private PriorityTaskQueueService priorityTaskQueueService;

    private static final UUID TEST_ID = UUID.randomUUID();
    private final DeferredResult<ResponseEntity<?>> mockDeferredResult = new DeferredResult<>();

    @AfterEach
    void tearDown() throws Exception {
        // Shutdown the internal thread pool after each test
        final Field poolField = PriorityTaskQueueService.class.getDeclaredField("enqueueWorkPool");
        poolField.setAccessible(true);
        final ExecutorService pool = (ExecutorService) poolField.get(priorityTaskQueueService);
        pool.shutdownNow();
    }

    @Test
    @DisplayName("Take: Should return tasks in order of their calculated priority")
    void take_ShouldRespectPriorityOrder() throws InterruptedException {
        final var lowPriorityTask = PrioritizedTask.builder()
                .request(new ClientRequest(TEST_ID, 100L))
                .build();
        final var highPriorityTask = PrioritizedTask.builder()
                .request(new ClientRequest(TEST_ID, 100L))
                .build();

        // Tasks are enqueued (order of enqueueing doesn't matter for PriorityBlockingQueue)
        priorityTaskQueueService.requeue(lowPriorityTask);
        priorityTaskQueueService.requeue(highPriorityTask);

        // The task with the HIGHER priority (smaller verification number) must be taken first
        final PrioritizedTask firstTask = priorityTaskQueueService.take();
        final PrioritizedTask secondTask = priorityTaskQueueService.take();

        assertEquals(firstTask, highPriorityTask, "Task (higher priority) must be taken first.");
        assertEquals(secondTask, lowPriorityTask, "Task (lower priority) must be taken second.");
    }

    @Test
    @DisplayName("Enqueue: Should successfully add multiple tasks concurrently")
    void enqueue_ShouldHandleConcurrentAdds() throws InterruptedException {
        final int NUM_TASKS = 50;
        CountDownLatch latch = new CountDownLatch(NUM_TASKS);

        // Given: Multiple concurrent threads calling enqueue
        ExecutorService concurrencyExecutor = Executors.newFixedThreadPool(10);
        for (int i = 0; i < NUM_TASKS; i++) {
            concurrencyExecutor.submit(() -> {
                priorityTaskQueueService.enqueue(new ClientRequest(TEST_ID, 100L), mockDeferredResult);
                latch.countDown();
            });
        }
        concurrencyExecutor.shutdown();

        // Wait until all enqueue calls have submitted the task to the internal work pool
        latch.await(5, TimeUnit.SECONDS);

        // Wait for the internal enqueueWorkPool to finish adding all tasks to the queue
        Thread.sleep(100);

        // The queue must contain exactly the number of submitted tasks
        assertEquals(NUM_TASKS, priorityTaskQueueService.size(), "The queue must contain all tasks submitted concurrently.");
    }

    @Test
    @DisplayName("Take: Should block when queue is empty and unblock when task is added")
    void take_ShouldBlockAndUnblock() throws Exception {
        // A separate thread attempts to take a task
        final CompletableFuture<PrioritizedTask> future = CompletableFuture.supplyAsync(() -> {
            try {
                return priorityTaskQueueService.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        });

        // The future should not be complete immediately, because queue is empty.
        assertFalse(future.isDone(), "Take() should block when the queue is empty.");

        // Requeue a task for immediately queueing
        final PrioritizedTask taskToAdd = PrioritizedTask.builder()
                .request(new ClientRequest(TEST_ID, 100L))
                .build();
        priorityTaskQueueService.requeue(taskToAdd);

        // The future should complete, and the returned task should be the one we added
        final PrioritizedTask takenTask = future.get(500, TimeUnit.MILLISECONDS);

        assertNotNull(takenTask, "Take() must unblock and return a task.");
        assertEquals(taskToAdd.getVerification(), takenTask.getVerification(), "The returned task must be the added task.");
    }
}
