package com.meant.api.module.cart.exception;

import com.meant.api.common.constant.ApiErrorCode;
import com.meant.api.common.exception.ApiException;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public class CartException extends RuntimeException implements ApiException {

    public enum BindingFailure {
        MISSING_ROUTING,
        AMBIGUOUS_ROUTING,
        STALE_OR_UNAVAILABLE,
        IDENTITY_MISMATCH,
        PROVIDER_FAILURE,
        IDEMPOTENT_REPLAY,
        CROSS_SCOPE_REPLAY
    }

    private static final String BAD_REQUEST_DETAIL = "The request could not be processed.";
    private static final String NOT_FOUND_DETAIL = "The requested resource was not found.";
    private static final String UPSTREAM_DETAIL = "Upstream service is temporarily unavailable.";

    private final HttpStatusCode status;
    private final ApiErrorCode errorCode;
    private final String safeMessage;
    private final BindingFailure bindingFailure;

    public CartException(String message) {
        this(message, null, HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, BAD_REQUEST_DETAIL, null);
    }

    public CartException(String message, Throwable cause) {
        this(message, cause, HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, BAD_REQUEST_DETAIL, null);
    }

    private CartException(
            String message,
            Throwable cause,
            HttpStatusCode status,
            ApiErrorCode errorCode,
            String safeMessage,
            BindingFailure bindingFailure
    ) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
        this.safeMessage = safeMessage;
        this.bindingFailure = bindingFailure;
    }

    public static CartException notFound(String message) {
        return new CartException(message, null, HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND, NOT_FOUND_DETAIL, null);
    }

    public static CartException rejected(String message) {
        String safeMessage = message == null || message.isBlank() ? BAD_REQUEST_DETAIL : message;
        return new CartException(safeMessage, null, HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, safeMessage, null);
    }

    public static CartException upstream(String message) {
        return upstream(message, null);
    }

    public static CartException upstream(String message, Throwable cause) {
        return new CartException(
                message,
                cause,
                HttpStatus.BAD_GATEWAY,
                ApiErrorCode.UPSTREAM_SERVICE_ERROR,
                UPSTREAM_DETAIL,
                null
        );
    }

    public static CartException binding(BindingFailure failure, String message) {
        return new CartException(
                message, null, HttpStatus.CONFLICT, ApiErrorCode.BAD_REQUEST,
                "The selected offer cannot be applied to this cart.", failure);
    }

    public static CartException bindingUpstream(String message, Throwable cause) {
        return new CartException(
                message, cause, HttpStatus.BAD_GATEWAY, ApiErrorCode.UPSTREAM_SERVICE_ERROR,
                UPSTREAM_DETAIL, BindingFailure.PROVIDER_FAILURE);
    }
}
