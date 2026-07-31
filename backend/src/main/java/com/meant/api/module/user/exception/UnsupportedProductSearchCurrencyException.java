package com.meant.api.module.user.exception;

import com.meant.api.common.constant.ApiErrorCode;
import com.meant.api.common.exception.ApiException;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public class UnsupportedProductSearchCurrencyException extends RuntimeException implements ApiException {

    private final HttpStatusCode status = HttpStatus.BAD_REQUEST;
    private final ApiErrorCode errorCode = ApiErrorCode.BAD_REQUEST;
    private final String safeMessage;

    public UnsupportedProductSearchCurrencyException(String currency) {
        this(currency, "USD");
    }

    public UnsupportedProductSearchCurrencyException(String currency, String preferredCurrency) {
        super("Product search currency " + currency + " does not match preferred currency " + preferredCurrency);
        this.safeMessage = "Use " + preferredCurrency + " for prices, or change your currency in Account settings.";
    }
}
