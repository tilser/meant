package com.meant.api.common.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ApiErrorCode {
    VALIDATION_FAILED("validation_failed"),
    BAD_REQUEST("bad_request"),
    AUTHENTICATION_REQUIRED("authentication_required"),
    FORBIDDEN("forbidden"),
    NOT_FOUND("not_found"),
    UPSTREAM_SERVICE_ERROR("upstream_service_error"),
    INTERNAL_ERROR("internal_error");

    private final String value;
}
