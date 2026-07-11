package com.meant.api.module.checkout.exception;

import com.meant.api.common.constant.ApiErrorCode;
import com.meant.api.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public class EmbeddedCheckoutException extends RuntimeException implements ApiException {
    private final HttpStatusCode status;
    private final String safeMessage;

    private EmbeddedCheckoutException(String message, HttpStatusCode status, String safeMessage) {
        super(message);
        this.status = status;
        this.safeMessage = safeMessage;
    }

    public static EmbeddedCheckoutException forbidden(String message) {
        return new EmbeddedCheckoutException(message, HttpStatus.FORBIDDEN,
                "You are not allowed to access this embedded checkout session.");
    }

    public static EmbeddedCheckoutException conflict(String message) {
        return new EmbeddedCheckoutException(message, HttpStatus.CONFLICT,
                "The embedded checkout session is no longer active.");
    }

    @Override
    public HttpStatusCode getStatus() {
        return status;
    }

    @Override
    public ApiErrorCode getErrorCode() {
        return status.value() == 403 ? ApiErrorCode.FORBIDDEN : ApiErrorCode.BAD_REQUEST;
    }

    @Override
    public String getSafeMessage() {
        return safeMessage;
    }
}
