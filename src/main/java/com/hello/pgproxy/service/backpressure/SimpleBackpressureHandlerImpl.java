package com.hello.pgproxy.service.backpressure;

import com.hello.pgproxy.configuration.ConcurrencyProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SimpleBackpressureHandlerImpl implements BackpressureHandler {
    private final ConcurrencyProperties concurrencyProperties;

    @Override
    public String getHandlerName() {
        return "simple";
    }

    @Override
    public int getScaleDownConcurrency(int concurrency) {
        return Math.max(1, (int)(concurrency * 0.95));
    }

    @Override
    public int getScaleUpConcurrency(int concurrency) {
        return Math.min(concurrency + 1, concurrencyProperties.getMax());
    }
}
