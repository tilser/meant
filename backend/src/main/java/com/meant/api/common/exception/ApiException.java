package com.meant.api.common.exception;

import com.meant.api.common.constant.ApiErrorCode;
import org.springframework.http.HttpStatusCode;

public interface ApiException {

    HttpStatusCode getStatus();

    ApiErrorCode getErrorCode();

    String getSafeMessage();
}
