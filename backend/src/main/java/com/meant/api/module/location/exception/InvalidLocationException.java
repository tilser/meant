package com.meant.api.module.location.exception;

import com.meant.api.common.constant.ApiErrorCode;
import com.meant.api.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public class InvalidLocationException extends RuntimeException implements ApiException {

    public InvalidLocationException(String message) {
        super(message);
    }

    @Override
    public HttpStatusCode getStatus() {
        return HttpStatus.BAD_REQUEST;
    }

    @Override
    public ApiErrorCode getErrorCode() {
        return ApiErrorCode.BAD_REQUEST;
    }

    @Override
    public String getSafeMessage() {
        return "Select a valid location from the suggestions.";
    }
}
