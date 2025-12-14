package com.hello.pgproxy.model;

public enum BackendExecutionStatus {
    COMPLETED,
    BACKPRESSURE_CONTROL_NEEDED,
    ERROR,
    UNKNOWN
}
