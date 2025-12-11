package com.hello.pgproxy.controller;

import com.hello.pgproxy.configuration.ResponseProperties;
import com.hello.pgproxy.model.ClientRequest;
import com.hello.pgproxy.service.PaymentProxyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

@RestController
@RequiredArgsConstructor
@Tag(name = "Payment Processing", description = "Endpoints for handling payment requests and managing backpressure.") // 컨트롤러 전체 태그
public class PaymentController {
    private final PaymentProxyService proxyService;
    private final ResponseProperties responseProperties;

    @Operation(
            summary = "Submit a payment request through the proxy",
            description = "Forwards the request to the backend after computing the verification number and handling prioritization."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Payment processed successfully."
    )
    @ApiResponse(
            responseCode = "400",
            description = "Invalid request or Wrong Verification Number."
    )
    @ApiResponse(
            responseCode = "503",
            description = "Backend Payment Gateway is overloaded. Request might be queued or rejected based on priority."
    )
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
