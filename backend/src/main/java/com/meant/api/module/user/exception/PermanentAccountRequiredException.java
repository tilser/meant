package com.meant.api.module.user.exception;

import com.meant.api.common.constant.ApiErrorCode;
import com.meant.api.common.exception.ApiException;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public class PermanentAccountRequiredException extends RuntimeException implements ApiException {

    private final HttpStatusCode status = HttpStatus.FORBIDDEN;
    private final ApiErrorCode errorCode = ApiErrorCode.PERMANENT_ACCOUNT_REQUIRED;
    private final String safeMessage = "Save your progress to use this feature.";

    public PermanentAccountRequiredException() {
        super("A permanent account is required for this operation");
    }
}
