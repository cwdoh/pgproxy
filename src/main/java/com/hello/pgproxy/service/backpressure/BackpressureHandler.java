package com.hello.pgproxy.service.backpressure;

public interface BackpressureHandler {
    String getHandlerName();

    /**
     * Caculate Scale-downed concurrency limit
     *
     * @param concurrency current concurrency limit
     * @return recommended concurrency limit for scale down
     */
    int getScaleDownConcurrency(int concurrency);

    /**
     * Caculate Scale-up concurrency limit
     *
     * @param concurrency current concurrency limit
     * @return recommended concurrency limit for scale up
     */
    int getScaleUpConcurrency(int concurrency);
}
