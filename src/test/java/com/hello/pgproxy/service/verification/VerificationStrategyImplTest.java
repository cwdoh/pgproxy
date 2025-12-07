package com.hello.pgproxy.service.verification;

import com.hello.pgproxy.model.ClientRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@ExtendWith(MockitoExtension.class)
class VerificationStrategyImplTest {
    @InjectMocks
    private VerificationStrategyImpl verificationStrategy;

    private static final UUID KNOWN_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final long KNOWN_AMOUNT = 1000L;
    private static final long KNOWN_VERIFICATION_NUMBER = 3579L;

    @Test
    void calculate_ShouldFindKnownVerificationNumber_ForFixedInput() {
        // Given
        ClientRequest request = new ClientRequest(KNOWN_ID, KNOWN_AMOUNT);

        // When
        long resultNumber = verificationStrategy.calculate(request);

        // Then
        assertEquals(KNOWN_VERIFICATION_NUMBER, resultNumber, "The verification number must match the pre-computed known value.");
    }

    @Test
    void calculate_ShouldFindDifferentNumber_WhenIdChanges() {
        // Given
        ClientRequest originalRequest = new ClientRequest(KNOWN_ID, KNOWN_AMOUNT);
        // different UUID
        ClientRequest changedRequest = new ClientRequest(UUID.fromString("00000000-0000-0000-0000-000000000001"), KNOWN_AMOUNT);

        // When
        long originalNumber = verificationStrategy.calculate(originalRequest);
        long changedNumber = verificationStrategy.calculate(changedRequest);

        // Then
        // The new number must NOT be the same as the original, proving the ID was used in the hash input.
        assertNotEquals(originalNumber, changedNumber, "Changing the ID must change the verification number.");
    }

    @Test
    void calculate_ShouldFindDifferentNumber_WhenAmountChanges() {
        // Given
        ClientRequest originalRequest = new ClientRequest(KNOWN_ID, KNOWN_AMOUNT);
        // A slightly different amount
        ClientRequest changedRequest = new ClientRequest(KNOWN_ID, KNOWN_AMOUNT + 1);

        // When
        long originalNumber = verificationStrategy.calculate(originalRequest);
        long changedNumber = verificationStrategy.calculate(changedRequest);

        // Then
        // The new number must NOT be the same as the original, proving the Amount was used in the hash input.
        assertNotEquals(originalNumber, changedNumber, "Changing the amount must change the verification number.");
    }
}
