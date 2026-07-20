package com.meant.api.module.agent.service.tool;

import com.meant.api.common.constant.ApiErrorCode;
import com.meant.api.module.agent.exception.AgentException;
import org.springframework.http.HttpStatus;

public final class AgentProductReadToolException {

    private AgentProductReadToolException() {
    }

    public static AgentException invalid(String message) {
        return new AgentException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, message);
    }

    public static AgentException invalid(String message, Throwable cause) {
        return new AgentException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, message, cause);
    }
}
