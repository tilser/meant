package com.meant.api.module.user.exception;

import com.meant.api.common.constant.ApiErrorCode;
import com.meant.api.common.exception.ApiException;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public class UnsupportedProductSearchCurrencyException extends RuntimeException implements ApiException {

    private static final String SAFE_MESSAGE = "Product search currently supports prices in USD only.";

    private final HttpStatusCode status = HttpStatus.BAD_REQUEST;
    private final ApiErrorCode errorCode = ApiErrorCode.BAD_REQUEST;
    private final String safeMessage = SAFE_MESSAGE;

    public UnsupportedProductSearchCurrencyException(String currency) {
        super("Unsupported product search currency: " + currency);
    }
}
