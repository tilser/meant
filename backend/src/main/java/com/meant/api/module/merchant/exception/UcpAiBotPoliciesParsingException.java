package com.meant.api.module.merchant.exception;

public class UcpAiBotPoliciesParsingException extends RuntimeException {

    public UcpAiBotPoliciesParsingException(Throwable cause) {
        super("Unable to parse UCP AI bot policies", cause);
    }
}
