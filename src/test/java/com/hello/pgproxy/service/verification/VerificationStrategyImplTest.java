package com.hello.pgproxy.service.verification;

import com.hello.pgproxy.model.ClientRequest;
import org.junit.jupiter.api.DisplayName;
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
    private static final long KNOWN_VERIFICATION_NUMBER = 3579L; // Pre-calculated expected output

    @Test
    @DisplayName("Calculate: Should find the known verification number for fixed input")
    void calculate_ShouldFindKnownVerificationNumber_ForFixedInput() {
        ClientRequest request = new ClientRequest(KNOWN_ID, KNOWN_AMOUNT);

        long resultNumber = verificationStrategy.calculate(request);

        // The result must exactly match the pre-computed known value
        assertEquals(KNOWN_VERIFICATION_NUMBER, resultNumber, "The verification number must match the pre-computed known value.");
    }

    @Test
    @DisplayName("Calculate: Should return a different number when the Request ID changes")
    void calculate_ShouldFindDifferentNumber_WhenIdChanges() {
        ClientRequest originalRequest = new ClientRequest(KNOWN_ID, KNOWN_AMOUNT);
        // Different UUID
        ClientRequest changedRequest = new ClientRequest(UUID.fromString("00000000-0000-0000-0000-000000000001"), KNOWN_AMOUNT);

        long originalNumber = verificationStrategy.calculate(originalRequest);
        long changedNumber = verificationStrategy.calculate(changedRequest);

        // The new number must NOT be the same as the original
        assertNotEquals(originalNumber, changedNumber, "Changing the ID must change the verification number.");
    }

    @Test
    @DisplayName("Calculate: Should return a different number when the Amount changes")
    void calculate_ShouldFindDifferentNumber_WhenAmountChanges() {
        ClientRequest originalRequest = new ClientRequest(KNOWN_ID, KNOWN_AMOUNT);
        ClientRequest changedRequest = new ClientRequest(KNOWN_ID, KNOWN_AMOUNT + 1);

        // Numbers are calculated for both
        long originalNumber = verificationStrategy.calculate(originalRequest);
        long changedNumber = verificationStrategy.calculate(changedRequest);

        // The new number must NOT be the same as the original
        assertNotEquals(originalNumber, changedNumber, "Changing the amount must change the verification number.");
    }
}
