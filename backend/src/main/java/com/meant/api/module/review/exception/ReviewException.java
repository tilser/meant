package com.meant.api.module.review.exception;

import com.meant.api.common.constant.ApiErrorCode;
import com.meant.api.common.exception.ApiException;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public class ReviewException extends RuntimeException implements ApiException {

    private static final String UPSTREAM_DETAIL = "Upstream service is temporarily unavailable.";

    private final HttpStatusCode status;
    private final ApiErrorCode errorCode;
    private final String safeMessage;

    public ReviewException(String message) {
        this(message, null, HttpStatus.BAD_GATEWAY, ApiErrorCode.UPSTREAM_SERVICE_ERROR, UPSTREAM_DETAIL);
    }

    public ReviewException(String message, Throwable cause) {
        this(message, cause, HttpStatus.BAD_GATEWAY, ApiErrorCode.UPSTREAM_SERVICE_ERROR, UPSTREAM_DETAIL);
    }

    private ReviewException(
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
}
