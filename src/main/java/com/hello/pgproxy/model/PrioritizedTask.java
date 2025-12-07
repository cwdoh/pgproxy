package com.hello.pgproxy.model;

import lombok.Data;

@Data
public class PrioritizedTask implements Comparable<PrioritizedTask> {
    private final ClientRequest request;
    private final Long verification;

    @Override
    public int compareTo(PrioritizedTask o) {
        return Long.compare(o.request.getAmount_cents(), this.request.getAmount_cents());
    }
}
