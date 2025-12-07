package com.hello.pgproxy.controller;

import com.hello.pgproxy.configuration.ResponseProperties;
import com.hello.pgproxy.model.ClientRequest;
import com.hello.pgproxy.service.PaymentProxyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

@RestController
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentProxyService proxyService;
    private final ResponseProperties responseProperties;

    @PostMapping("/payments")
    public DeferredResult<ResponseEntity<?>> processPayment(@RequestBody ClientRequest request) {
        DeferredResult<ResponseEntity<?>> deferredResponse = createDeferredResult();

        if (request.getId() == null || request.getAmount_cents() == null) {
            deferredResponse.setResult(ResponseEntity.badRequest().body("Missing ID or Amount"));
            return deferredResponse;
        }

        proxyService.enqueue(request, deferredResponse);

        return deferredResponse;
    }

    private DeferredResult<ResponseEntity<?>> createDeferredResult() {
        DeferredResult<ResponseEntity<?>> result = new DeferredResult<>(responseProperties.getTimeout());
        result.onTimeout(() -> result.setErrorResult(
                ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body("Request Timed Out"))
        );

        return result;
    }
}
