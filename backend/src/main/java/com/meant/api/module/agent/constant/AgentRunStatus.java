package com.meant.api.module.agent.constant;

public enum AgentRunStatus {
    QUEUED,
    RUNNING,
    WAITING_FOR_USER,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean terminal() {
        return this == WAITING_FOR_USER || this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
