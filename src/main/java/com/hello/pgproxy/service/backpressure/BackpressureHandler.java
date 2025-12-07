package com.hello.pgproxy.service.backpressure;

public interface BackpressureHandler {
    String getHandlerName();

    int getScaleDownConcurrency(int concurrency);

    int getScaleUpConcurrency(int concurrency);
}
