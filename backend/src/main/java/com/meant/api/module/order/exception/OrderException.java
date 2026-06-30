package com.meant.api.module.order.exception;

import com.meant.api.common.constant.ApiErrorCode;
import com.meant.api.common.exception.ApiException;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public class OrderException extends RuntimeException implements ApiException {

    private static final String BAD_REQUEST_DETAIL = "The request could not be processed.";
    private static final String FORBIDDEN_DETAIL = "Webhook signature verification failed.";
    private static final String NOT_FOUND_DETAIL = "The requested resource was not found.";
    private static final String UPSTREAM_DETAIL = "Upstream service is temporarily unavailable.";

    private final HttpStatusCode status;
    private final ApiErrorCode errorCode;
    private final String safeMessage;

    public OrderException(String message) {
        this(message, null, HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, BAD_REQUEST_DETAIL);
    }

    public OrderException(String message, Throwable cause) {
        this(message, cause, HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, BAD_REQUEST_DETAIL);
    }

    private OrderException(
            String message,
            Throwable cause,
            HttpStatusCode status,
            ApiErrorCode errorCode,
            String safeMessage
    ) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
        this.safeMessage = safeMessage;
    }

    public static OrderException forbidden(String message) {
        return new OrderException(message, null, HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN, FORBIDDEN_DETAIL);
    }

    public static OrderException notFound(String message) {
        return new OrderException(message, null, HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND, NOT_FOUND_DETAIL);
    }

    public static OrderException rejected(String message) {
        String safeMessage = message == null || message.isBlank() ? BAD_REQUEST_DETAIL : message;
        return new OrderException(safeMessage, null, HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, safeMessage);
    }

    public static OrderException upstream(String message) {
        return upstream(message, null);
    }

    public static OrderException upstream(String message, Throwable cause) {
        return new OrderException(
                message,
                cause,
                HttpStatus.BAD_GATEWAY,
                ApiErrorCode.UPSTREAM_SERVICE_ERROR,
                UPSTREAM_DETAIL
        );
    }
}
