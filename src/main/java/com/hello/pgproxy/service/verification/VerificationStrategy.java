package com.hello.pgproxy.service.verification;

import com.hello.pgproxy.model.ClientRequest;

public interface VerificationStrategy {
    /**
     * This interface define the method for computing verification code.
     * Initial implementation can be simple, but it can be changed for the purpose:
     * e.g. if someone replay request with the same request many times, does all of them should be computed?
     *
     * @param request Payment request
     * @return Calculated verification code
     */
    long calculate(ClientRequest request);
}
