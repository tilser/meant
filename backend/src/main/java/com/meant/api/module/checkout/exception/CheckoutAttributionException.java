package com.meant.api.module.checkout.exception;

import com.meant.api.common.constant.ApiErrorCode;
import com.meant.api.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public class CheckoutAttributionException extends RuntimeException implements ApiException {
    private final HttpStatusCode status;
    private final String safeMessage;

    private CheckoutAttributionException(String message, HttpStatusCode status, String safeMessage) {
        super(message);
        this.status = status;
        this.safeMessage = safeMessage;
    }

    public static CheckoutAttributionException forbidden(String message) {
        return new CheckoutAttributionException(
                message,
                HttpStatus.FORBIDDEN,
                "You are not allowed to attribute this checkout."
        );
    }

    public static CheckoutAttributionException conflict(String message) {
        return new CheckoutAttributionException(
                message,
                HttpStatus.CONFLICT,
                "The checkout attempt is no longer current."
        );
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
