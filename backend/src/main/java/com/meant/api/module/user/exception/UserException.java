package com.meant.api.module.user.exception;

import com.meant.api.common.constant.ApiErrorCode;
import com.meant.api.common.exception.ApiException;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public class UserException extends RuntimeException implements ApiException {

    private static final String BAD_REQUEST_DETAIL = "The request could not be processed.";
    private static final String CONFLICT_DETAIL = "The resource changed while the request was being processed.";
    private static final String FORBIDDEN_DETAIL = "You are not allowed to access this resource.";
    private static final String NOT_FOUND_DETAIL = "The requested resource was not found.";

    private final HttpStatusCode status;
    private final ApiErrorCode errorCode;
    private final String safeMessage;

    public UserException(String message) {
        this(message, null, HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, BAD_REQUEST_DETAIL);
    }

    public UserException(String message, Throwable cause) {
        this(message, cause, HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, BAD_REQUEST_DETAIL);
    }

    private UserException(
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

    public static UserException forbidden(String message) {
        return new UserException(message, null, HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN, FORBIDDEN_DETAIL);
    }

    public static UserException notFound(String message) {
        return new UserException(message, null, HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND, NOT_FOUND_DETAIL);
    }

    public static UserException conflict(String message) {
        return new UserException(message, null, HttpStatus.CONFLICT, ApiErrorCode.BAD_REQUEST, CONFLICT_DETAIL);
    }
}
