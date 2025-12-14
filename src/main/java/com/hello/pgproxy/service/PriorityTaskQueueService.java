package com.hello.pgproxy.service;

import com.hello.pgproxy.model.ClientRequest;
import com.hello.pgproxy.model.PrioritizedTask;
import com.hello.pgproxy.service.verification.VerificationStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Service responsible for queue of payment requests with a focus on revenue maximization.
 * Prioritization:
 * - Uses a {@link PriorityBlockingQueue} to order requests by {@link PrioritizedTask}
 * - This ensures that higher-value request are processed first
 * Trade-off:
 * - While enqueue() introduces a slight computational overhead, but this cost is negligible to achieve the goal.
 */
@Service
@RequiredArgsConstructor
public class PriorityTaskQueueService {
    private final VerificationStrategy verificationStrategy;

    // Thread-safe queue that orders payment requests
    private final PriorityBlockingQueue<PrioritizedTask> queue = new PriorityBlockingQueue<>();

    // Allocate the dedicate thread pool for cpu intensive works
    private final int availableCores = Runtime.getRuntime().availableProcessors();
    private final ExecutorService enqueueWorkPool = Executors.newFixedThreadPool(availableCores);

    public void enqueue(ClientRequest request, DeferredResult<ResponseEntity<?>> deferredResult) {
        enqueueWorkPool.submit(() -> {
            final Long verification = verificationStrategy.calculate(request);
            final var task = PrioritizedTask.builder()
                    .request(request)
                    .verification(verification)
                    .deferredResponse(deferredResult)
                    .build();

            queue.add(task);
        });
    }

    public void requeue(PrioritizedTask task) {
        queue.add(task);
    }

    public PrioritizedTask take() throws InterruptedException {
        return queue.take();
    }

    public int getRemainingTaskCount() {
        return queue.size();
    }
}
