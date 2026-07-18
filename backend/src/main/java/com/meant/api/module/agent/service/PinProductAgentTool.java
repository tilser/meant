package com.meant.api.module.agent.service;

import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import org.springframework.stereotype.Service;

@Service
public class PinProductAgentTool extends AbstractAgentProductInteractionTool {

    private static final AgentToolDescriptor DESCRIPTOR = new AgentToolDescriptor(
            "pin_product",
            "Persistently pin an exact product previously shown in this conversation.",
            """
            {"type":"object","properties":{"canonicalProductKey":{"type":"string","minLength":1,"maxLength":200},"offerKey":{"type":"string","minLength":1,"maxLength":200}},"required":["canonicalProductKey"],"additionalProperties":false}
            """,
            "1",
            AgentToolRisk.REVERSIBLE_MUTATION
    );

    public PinProductAgentTool(AgentJsonSupport json, AgentProductInteractionService interactionService) {
        super(DESCRIPTOR, Operation.PIN, json, interactionService);
    }
}
