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

    public static AgentException actionInProgress() {
        return new AgentException(
                HttpStatus.CONFLICT,
                ApiErrorCode.AGENT_ACTION_IN_PROGRESS,
                "This action is already in progress."
        );
    }

    public static AgentException actionUncertain(HttpStatus status, String message) {
        return new AgentException(status, ApiErrorCode.AGENT_ACTION_UNCERTAIN, message);
    }

    public static AgentException actionUncertain(HttpStatus status, String message, Throwable cause) {
        return new AgentException(status, ApiErrorCode.AGENT_ACTION_UNCERTAIN, message, cause);
    }

    public static AgentException cursorExpired() {
        return new AgentException(
                HttpStatus.GONE,
                ApiErrorCode.AGENT_CURSOR_EXPIRED,
                "The event cursor has expired. Reload the run snapshot and reconnect from its latest cursor."
        );
    }

    public static AgentException streamLimit() {
        return new AgentException(
                HttpStatus.TOO_MANY_REQUESTS,
                ApiErrorCode.AGENT_STREAM_LIMIT,
                "Too many agent event streams are already open. Close another stream and try again."
        );
    }
}
