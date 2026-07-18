package com.meant.api.module.agent.service;

import com.meant.api.common.constant.ApiErrorCode;
import com.meant.api.module.agent.exception.AgentException;
import org.springframework.http.HttpStatus;

final class AgentProductReadToolException {

    private AgentProductReadToolException() {
    }

    static AgentException invalid(String message) {
        return new AgentException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, message);
    }

    static AgentException invalid(String message, Throwable cause) {
        return new AgentException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, message, cause);
    }
}
