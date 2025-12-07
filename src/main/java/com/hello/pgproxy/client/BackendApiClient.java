package com.hello.pgproxy.client;

import com.hello.pgproxy.model.BackendRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class BackendApiClient {
    private final RestTemplate backendRestTemplate;
    @Value("${backend.url}")
    private String backendUrl;

    // Forward to Backend
    public ResponseEntity<String> postForEntity(BackendRequest request) {
        return backendRestTemplate.postForEntity(backendUrl, request, String.class);
    }
}
