package com.hello.pgproxy.service;

import com.hello.pgproxy.client.BackendApiClient;
import com.hello.pgproxy.model.BackendExecutionStatus;
import com.hello.pgproxy.model.BackendRequest;
import com.hello.pgproxy.model.ClientRequest;
import com.hello.pgproxy.model.PrioritizedTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentExecutor {
    private final BackendApiClient backendApiClient;

    public BackendExecutionStatus execute(PrioritizedTask task) {
        try {
            final ResponseEntity<?> response = forwardRequestToBackend(task);
            task.getDeferredResponse().setResult(response);
            log.info("Payment has been completed for request id: {}", task.getRequest().getId());

            // Success: Delegate scale-up logic
            return BackendExecutionStatus.COMPLETED;
        } catch (HttpServerErrorException.ServiceUnavailable e) {
            log.warn("Payment service unavailable for request id: {}", task.getRequest().getId(), e);

            // Failure: Delegate backpressure handling logic
            return BackendExecutionStatus.BACKPRESSURE_CONTROL_NEEDED;
        } catch (HttpClientErrorException e) {
            // Client Error (400) - Pass through to client
            final ResponseEntity responseEntity = ResponseEntity
                    .status(e.getStatusCode())
                    .body(e.getResponseBodyAsString());

            task.getDeferredResponse().setResult(responseEntity);
            log.warn("Got client error for request id: {}", task.getRequest().getId(), e);

            return BackendExecutionStatus.ERROR;
        } catch (Exception e) {
            task.getDeferredResponse().setResult(
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
            );
            log.error("Got error for request id: {}", task.getRequest().getId(), e);

            return BackendExecutionStatus.ERROR;
        }
    }

    private ResponseEntity<?> forwardRequestToBackend(PrioritizedTask task) {
        final ClientRequest request = task.getRequest();
        final BackendRequest body = BackendRequest.builder()
                .id(request.getId())
                .amount_cents(request.getAmount_cents())
                .verification(task.getVerification())
                .build();

        return backendApiClient.postForEntity(body);
    }
}