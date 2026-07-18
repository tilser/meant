package com.meant.api.module.agent.service;

import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentToolExecutionResult;

public interface AgentTool {

    AgentToolDescriptor descriptor();

    AgentToolExecutionResult execute(AgentToolExecutionContext context, String argumentsJson);
}
