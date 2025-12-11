package com.hello.pgproxy.model;

import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.DeferredResult;

@Data
public class PrioritizedTask implements Comparable<PrioritizedTask> {
    private final ClientRequest request;
    // Recommend verification value here, because possibly computing verification re-arrange to the another spot.
    private final Long verification;
    // Task will hold the response until request really processed.
    private final DeferredResult<ResponseEntity<?>> deferredResponse;

    // This ensures that which task should be forwarded first to achieve the (maybe business) goal.
    @Override
    public int compareTo(PrioritizedTask o) {
        return Long.compare(o.request.getAmount_cents(), this.request.getAmount_cents());
    }
}
