package com.hello.pgproxy.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties("proxy.concurrency")
public class ConcurrencyProperties {
    private int start;
    private int max;
    private long unpauseDelay;
    private long busyWaitInterval;
    private long modifiableInterval;
}
