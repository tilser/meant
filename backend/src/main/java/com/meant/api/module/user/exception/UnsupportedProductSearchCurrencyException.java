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
        this(currency, preferredCurrency, false);
    }

    private UnsupportedProductSearchCurrencyException(String currency, String preferredCurrency, boolean mixed) {
        super(mixed
                ? "Product search contains mixed currencies"
                : "Product search currency " + currency + " does not match preferred currency " + preferredCurrency);
        this.safeMessage = mixed
                ? "Price amounts use different currencies. Use only one currency in a search."
                : "This currency could not be used for product search.";
    }

    public static UnsupportedProductSearchCurrencyException mixed(String preferredCurrency) {
        return new UnsupportedProductSearchCurrencyException(null, preferredCurrency, true);
    }
}
