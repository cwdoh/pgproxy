package com.hello.pgproxy.service;

import com.hello.pgproxy.client.BackendApiClient;
import com.hello.pgproxy.model.BackendExecutionStatus;
import com.hello.pgproxy.model.BackendRequest;
import com.hello.pgproxy.model.ClientRequest;
import com.hello.pgproxy.model.PrioritizedTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentExecutionService Business Logic Test")
class PaymentExecutorTest {
    @Mock
    private BackendApiClient backendApiClient;

    @InjectMocks
    private PaymentExecutor paymentExecutor;

    private static final UUID TEST_ID = UUID.randomUUID();
    private static long VERIFY_NUMBER = 1004L;

    private final ClientRequest mockClientRequest = new ClientRequest(TEST_ID, 5000L);
    private final DeferredResult<ResponseEntity<?>> mockDeferredResponse = mock();
    private final PrioritizedTask mockTask = PrioritizedTask.builder()
            .request(mockClientRequest)
            .verification(VERIFY_NUMBER)
            .deferredResponse(mockDeferredResponse)
            .build();

    @Test
    @DisplayName("Should process request successfully and increase concurrency limit")
    void execute_SuccessScenario_ShouldIncreaseLimit() {
        // GIVEN: Successful response from backend
        ResponseEntity successResponse = ResponseEntity.ok("Payment successful");
        when(backendApiClient.postForEntity(any(BackendRequest.class)))
                .thenReturn(successResponse);

        // WHEN
        final var result = paymentExecutor.execute(mockTask);

        // THEN
        assertEquals(BackendExecutionStatus.COMPLETED, result);

        // Verify the deferred response is set with the successful result
        verify(mockDeferredResponse, times(1)).setResult(successResponse);
    }

    @Test
    @DisplayName("Should handle 503 Service Unavailable (Backpressure) and delegate to policy manager")
    void execute_ServiceUnavailable_ShouldHandleBackpressure() {
        // GIVEN: 503 Service Unavailable exception thrown by backend
         HttpServerErrorException serviceUnavailableException = HttpServerErrorException.create(
             HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", null, null, null);

        when(backendApiClient.postForEntity(any(BackendRequest.class)))
                .thenThrow(serviceUnavailableException);

        // WHEN
        final var result = paymentExecutor.execute(mockTask);

        // THEN
        assertEquals(BackendExecutionStatus.BACKPRESSURE_CONTROL_NEEDED, result);

        // Verify the deferred response is NOT set
        verify(mockDeferredResponse, never()).setResult(any());
    }

    @Test
    @DisplayName("Should handle 4xx Client Error and pass response through")
    void execute_HttpClientError_ShouldSetDeferredResponse() {
        // GIVEN: 400 Bad Request exception thrown by backend
        HttpStatus clientErrorStatus = HttpStatus.BAD_REQUEST;
        String errorBody = "{\"error\": \"Invalid parameters\"}";
        HttpClientErrorException clientErrorException =
                HttpClientErrorException.create(clientErrorStatus, "Bad Request", null, errorBody.getBytes(), null);

        when(backendApiClient.postForEntity(any(BackendRequest.class)))
                .thenThrow(clientErrorException);

        // WHEN
        final var result = paymentExecutor.execute(mockTask);

        // THEN
        assertEquals(BackendExecutionStatus.ERROR, result);

        // 1. Capture the response set to the deferred response
        verify(mockDeferredResponse, times(1)).setResult(any(ResponseEntity.class));

        // 2. Verify the response entity status and body are correctly passed through
        verify(mockDeferredResponse).setResult(argThat(response -> {
            // The argument passed to setResult should have the same status and body
            return response.getStatusCode() == clientErrorStatus &&
                    response.getBody().equals(errorBody);
        }));
    }

    @Test
    @DisplayName("Should handle generic exception and return 500 Internal Server Error")
    void execute_GenericException_ShouldReturn500() {
        // GIVEN: A generic runtime exception (not specific HTTP error)
        RuntimeException genericException = new RuntimeException("DB Connection Failed");
        when(backendApiClient.postForEntity(any(BackendRequest.class)))
                .thenThrow(genericException);

        // WHEN
        final var result = paymentExecutor.execute(mockTask);

        // THEN
        assertEquals(BackendExecutionStatus.ERROR, result);

        // 1. Capture the response set to the deferred response
        verify(mockDeferredResponse, times(1)).setResult(any(ResponseEntity.class));

        // 2. Verify the response entity is 500
        verify(mockDeferredResponse).setResult(argThat(response ->
                response.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR
        ));
    }
}
