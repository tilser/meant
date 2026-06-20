package com.meant.api.module.merchant.exception;

import com.meant.api.common.constant.ApiErrorCode;
import com.meant.api.common.exception.ApiException;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public class MerchantIdentityLinkException extends RuntimeException implements ApiException {

    private static final String BAD_REQUEST_DETAIL = "The request could not be processed.";
    private static final String NOT_FOUND_DETAIL = "The requested resource was not found.";
    private static final String UPSTREAM_DETAIL = "Upstream service is temporarily unavailable.";

    private final HttpStatusCode status;
    private final ApiErrorCode errorCode;
    private final String safeMessage;

    public MerchantIdentityLinkException(String message) {
        this(message, null, HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, BAD_REQUEST_DETAIL);
    }

    public MerchantIdentityLinkException(String message, Throwable cause) {
        this(message, cause, HttpStatus.BAD_GATEWAY, ApiErrorCode.UPSTREAM_SERVICE_ERROR, UPSTREAM_DETAIL);
    }

    private MerchantIdentityLinkException(
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

    public static MerchantIdentityLinkException notFound(String message) {
        return new MerchantIdentityLinkException(
                message,
                null,
                HttpStatus.NOT_FOUND,
                ApiErrorCode.NOT_FOUND,
                NOT_FOUND_DETAIL
        );
    }

    public static MerchantIdentityLinkException upstream(String message, Throwable cause) {
        return new MerchantIdentityLinkException(
                message,
                cause,
                HttpStatus.BAD_GATEWAY,
                ApiErrorCode.UPSTREAM_SERVICE_ERROR,
                UPSTREAM_DETAIL
        );
    }
}
