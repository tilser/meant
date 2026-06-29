package com.meant.api.plugin.transport.registry;

public class UnknownUcpToolException extends RuntimeException {

    public UnknownUcpToolException(String toolName) {
        super("Unknown UCP tool: " + toolName);
    }
}
