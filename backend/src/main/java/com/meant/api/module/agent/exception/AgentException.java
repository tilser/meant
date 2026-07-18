package com.meant.api.module.agent.exception;

import com.meant.api.common.constant.ApiErrorCode;
import com.meant.api.common.exception.ApiException;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public class AgentException extends RuntimeException implements ApiException {

    private final HttpStatusCode status;
    private final ApiErrorCode errorCode;
    private final String safeMessage;

    public AgentException(HttpStatus status, ApiErrorCode errorCode, String safeMessage) {
        super(safeMessage);
        this.status = status;
        this.errorCode = errorCode;
        this.safeMessage = safeMessage;
    }

    public AgentException(HttpStatus status, ApiErrorCode errorCode, String safeMessage, Throwable cause) {
        super(safeMessage, cause);
        this.status = status;
        this.errorCode = errorCode;
        this.safeMessage = safeMessage;
    }

    public static AgentException notFound() {
        return new AgentException(HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND, "The agent resource was not found.");
    }

    public static AgentException disabled() {
        return new AgentException(
                HttpStatus.SERVICE_UNAVAILABLE,
                ApiErrorCode.AGENT_DISABLED,
                "The shopping agent is temporarily unavailable."
        );
    }

    public static AgentException conflict(String message) {
        return new AgentException(HttpStatus.CONFLICT, ApiErrorCode.AGENT_CONFLICT, message);
    }

    public static AgentException cursorExpired() {
        return new AgentException(
                HttpStatus.GONE,
                ApiErrorCode.AGENT_CURSOR_EXPIRED,
                "The event cursor has expired. Reload the run snapshot and reconnect from its latest cursor."
        );
    }
}
