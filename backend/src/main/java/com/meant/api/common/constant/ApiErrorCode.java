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
    AGENT_DISABLED("agent_disabled"),
    AGENT_CONFLICT("agent_conflict"),
    AGENT_ACTION_IN_PROGRESS("agent_action_in_progress"),
    AGENT_ACTION_UNCERTAIN("agent_action_uncertain"),
    AGENT_CURSOR_EXPIRED("agent_cursor_expired"),
    AGENT_DAILY_MESSAGE_LIMIT("agent_daily_message_limit"),
    AGENT_STREAM_LIMIT("agent_stream_limit"),
    UPSTREAM_SERVICE_ERROR("upstream_service_error"),
    INTERNAL_ERROR("internal_error");

    private final String value;
}
