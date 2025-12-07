package com.hello.pgproxy.model;

import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.DeferredResult;

@Data
public class PrioritizedTask implements Comparable<PrioritizedTask> {
    private final ClientRequest request;
    private final Long verification;
    private final DeferredResult<ResponseEntity<?>> deferredResponse;

    @Override
    public int compareTo(PrioritizedTask o) {
        return Long.compare(o.request.getAmount_cents(), this.request.getAmount_cents());
    }
}
