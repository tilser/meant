package com.meant.api.module.merchant.exception;

public class UcpTransportsParsingException extends RuntimeException {

    public UcpTransportsParsingException(Throwable cause) {
        super("Unable to parse UCP transports", cause);
    }
}
