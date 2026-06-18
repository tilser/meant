package com.meant.api.module.merchant.exception;

import com.meant.api.common.constant.ApiErrorCode;
import com.meant.api.common.exception.ApiException;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public class MerchantCatalogSearchException extends RuntimeException implements ApiException {

    private static final String NOT_FOUND_DETAIL = "The requested resource was not found.";
    private static final String UPSTREAM_DETAIL = "Upstream service is temporarily unavailable.";

    private final HttpStatusCode status;
    private final ApiErrorCode errorCode;
    private final String safeMessage;

    public MerchantCatalogSearchException(String message) {
        this(message, null, HttpStatus.BAD_GATEWAY, ApiErrorCode.UPSTREAM_SERVICE_ERROR, UPSTREAM_DETAIL);
    }

    public MerchantCatalogSearchException(String message, Throwable cause) {
        this(message, cause, HttpStatus.BAD_GATEWAY, ApiErrorCode.UPSTREAM_SERVICE_ERROR, UPSTREAM_DETAIL);
    }

    private MerchantCatalogSearchException(
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

    public static MerchantCatalogSearchException notFound(String message) {
        return new MerchantCatalogSearchException(
                message,
                null,
                HttpStatus.NOT_FOUND,
                ApiErrorCode.NOT_FOUND,
                NOT_FOUND_DETAIL
        );
    }
}
