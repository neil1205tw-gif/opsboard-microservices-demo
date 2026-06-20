package com.opsboard.ops.exception;

public class UnknownServiceException extends RuntimeException {

    public UnknownServiceException(String serviceName) {
        super("Unknown service: " + serviceName);
    }
}
