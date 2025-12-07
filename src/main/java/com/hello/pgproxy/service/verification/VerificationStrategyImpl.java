package com.hello.pgproxy.service.verification;

import com.hello.pgproxy.model.ClientRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
@Slf4j
public class VerificationStrategyImpl implements VerificationStrategy {
    @Override
    public long calculate(ClientRequest request) {
        final String PREFIX = "abcd";
        final String baseString = request.getId().toString() + request.getAmount_cents();

        long verification = 0;

        while (true) {
            final String input = baseString + verification;

            // Compute the SHA256 hash of the string
            final String hash = calculateSha256(input);

            // If hash result starts with the given prefix, return verfication number
            if (hash.startsWith(PREFIX)) {
                return verification;
            }

            // Otherwise, increment verification and repeat.
            verification ++;

            // TODO: Add a safety break for very long runs
        }
    }

    private static String calculateSha256(String input) {
        final String ALGORITHM = "SHA-256";

        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] encodedHashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().withLowerCase().formatHex(encodedHashBytes);
        } catch (NoSuchAlgorithmException e) {
            log.error("{} algorithm not available", ALGORITHM, e);
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
