package com.meant.api.plugin.transport;

public class UnknownUcpToolException extends RuntimeException {

    public UnknownUcpToolException(String toolName) {
        super("Unknown UCP tool: " + toolName);
    }
}
