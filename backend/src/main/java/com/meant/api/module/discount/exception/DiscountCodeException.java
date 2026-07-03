package com.meant.api.module.discount.exception;

import com.meant.api.common.constant.ApiErrorCode;
import com.meant.api.common.exception.ApiException;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public class DiscountCodeException extends RuntimeException implements ApiException {

    private static final String BAD_REQUEST_DETAIL = "The request could not be processed.";
    private static final String NOT_FOUND_DETAIL = "The requested resource was not found.";
    private static final String UPSTREAM_DETAIL = "Upstream service is temporarily unavailable.";

    private final HttpStatusCode status;
    private final ApiErrorCode errorCode;
    private final String safeMessage;

    public DiscountCodeException(String message) {
        this(message, null, HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, BAD_REQUEST_DETAIL);
    }

    public DiscountCodeException(String message, Throwable cause) {
        this(message, cause, HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, BAD_REQUEST_DETAIL);
    }

    private DiscountCodeException(
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

    public static DiscountCodeException notFound(String message) {
        return new DiscountCodeException(message, null, HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND, NOT_FOUND_DETAIL);
    }

    public static DiscountCodeException upstream(String message, Throwable cause) {
        return new DiscountCodeException(
                message,
                cause,
                HttpStatus.BAD_GATEWAY,
                ApiErrorCode.UPSTREAM_SERVICE_ERROR,
                UPSTREAM_DETAIL
        );
    }
}
