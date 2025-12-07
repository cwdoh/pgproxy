package com.hello.pgproxy.configuration;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@ConfigurationProperties(prefix = "endpoints")
@Getter
public class RestClientConfig {
    private String url;

    @Bean
    public RestTemplate backendRestTemplate() {
        return new RestTemplate();
    }
}
