package com.hello.pgproxy.service.verification;

import com.hello.pgproxy.model.ClientRequest;

public interface VerificationStrategy {
    long calculate(ClientRequest request);
}
