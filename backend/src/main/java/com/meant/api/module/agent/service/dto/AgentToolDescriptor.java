package com.meant.api.module.agent.service.dto;

import com.meant.api.module.agent.constant.AgentToolRisk;

public record AgentToolDescriptor(
        String name,
        String description,
        String inputSchemaJson,
        String version,
        AgentToolRisk riskClass
) {

    public AgentModelToolDefinition modelDefinition() {
        return new AgentModelToolDefinition(name, description, inputSchemaJson);
    }
}
