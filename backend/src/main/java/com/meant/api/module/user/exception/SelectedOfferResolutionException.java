package com.meant.api.module.user.exception;

import com.meant.api.common.constant.ApiErrorCode;
import com.meant.api.common.exception.ApiException;
import java.util.Locale;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public class SelectedOfferResolutionException extends RuntimeException implements ApiException {

    public enum Failure {
        UNKNOWN_OR_EXPIRED,
        WRONG_USER,
        STALE_OR_UNAVAILABLE,
        IDENTITY_MISMATCH,
        AMBIGUOUS_PROVENANCE,
        UNSUPPORTED_SELECTION,
        PROVIDER_FAILURE
    }

    private final Failure failure;
    private final HttpStatusCode status;
    private final ApiErrorCode errorCode;
    private final String safeMessage;

    private SelectedOfferResolutionException(Failure failure, String message, HttpStatus status) {
        super(message);
        this.failure = failure;
        this.status = status;
        this.errorCode = status == HttpStatus.NOT_FOUND ? ApiErrorCode.NOT_FOUND : ApiErrorCode.BAD_REQUEST;
        this.safeMessage = status == HttpStatus.NOT_FOUND
                ? "The selected offer is unknown or expired."
                : "The selected offer is not currently eligible for cart.";
    }

    public static SelectedOfferResolutionException unknownOrExpired() {
        return new SelectedOfferResolutionException(
                Failure.UNKNOWN_OR_EXPIRED,
                "Selected offer is unknown, expired, or owned by another user",
                HttpStatus.NOT_FOUND
        );
    }

    public static SelectedOfferResolutionException wrongUser() {
        return new SelectedOfferResolutionException(
                Failure.WRONG_USER,
                "Selected offer is owned by another user",
                HttpStatus.NOT_FOUND
        );
    }

    public static SelectedOfferResolutionException rejected(Failure failure, String message) {
        return new SelectedOfferResolutionException(failure, message, HttpStatus.CONFLICT);
    }

    public String getSafeReason() {
        return switch (failure) {
            case UNKNOWN_OR_EXPIRED, WRONG_USER -> "unknown_or_expired";
            default -> failure.name().toLowerCase(Locale.ROOT);
        };
    }
}
