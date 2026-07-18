package com.meant.api.module.agent.constant;

public enum AgentRunEventType {
    RUN_STARTED("run.started"),
    ASSISTANT_DELTA("assistant.delta"),
    ASSISTANT_COMPLETED("assistant.completed"),
    TOOL_PROPOSED("tool.proposed"),
    TOOL_STARTED("tool.started"),
    TOOL_COMPLETED("tool.completed"),
    TOOL_FAILED("tool.failed"),
    ARTIFACT_UPSERTED("artifact.upserted"),
    CART_CHANGED("cart.changed"),
    CHECKOUT_READY("checkout.ready"),
    RUN_WAITING_FOR_USER("run.waiting_for_user"),
    RUN_COMPLETED("run.completed"),
    RUN_FAILED("run.failed"),
    RUN_CANCELLED("run.cancelled");

    private final String wireValue;

    AgentRunEventType(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
