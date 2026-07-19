package com.meant.api.module.agent.service;

import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AgentToolRegistry {

    private final Map<String, AgentTool> tools;

    public AgentToolRegistry(List<AgentTool> registeredTools) {
        Map<String, AgentTool> byName = new LinkedHashMap<>();
        for (AgentTool tool : registeredTools) {
            String name = tool.descriptor().name();
            if ("complete_checkout".equals(name)
                    || tool.descriptor().riskClass() == AgentToolRisk.IRREVERSIBLE_MUTATION) {
                throw new IllegalStateException("Irreversible commerce tools must never be registered for the agent");
            }
            if (byName.putIfAbsent(name, tool) != null) {
                throw new IllegalStateException("Duplicate agent tool registration: " + name);
            }
        }
        tools = Collections.unmodifiableMap(new LinkedHashMap<>(byName));
    }

    public List<AgentToolDescriptor> descriptors() {
        return tools.values().stream().map(AgentTool::descriptor).toList();
    }

    public AgentTool required(String name) {
        AgentTool tool = tools.get(name);
        if (tool == null) {
            throw new AgentException(
                    HttpStatus.BAD_REQUEST,
                    com.meant.api.common.constant.ApiErrorCode.BAD_REQUEST,
                    "The requested agent tool is not available."
            );
        }
        return tool;
    }
}
