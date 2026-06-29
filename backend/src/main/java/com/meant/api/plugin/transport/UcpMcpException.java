package com.meant.api.plugin.transport;

public class UcpMcpException extends RuntimeException {

    public UcpMcpException(String message) {
        super(message);
    }

    public UcpMcpException(String message, Throwable cause) {
        super(message, cause);
    }
}
