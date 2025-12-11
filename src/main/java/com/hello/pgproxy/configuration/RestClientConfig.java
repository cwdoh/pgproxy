package com.hello.pgproxy.configuration;

import lombok.Getter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@Getter
public class RestClientConfig {
    @Bean
    public RestTemplate backendRestTemplate() {
        return new RestTemplate();
    }
}
