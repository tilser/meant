package com.meant.api.plugin.transport.client;

import com.meant.api.plugin.transport.dto.McpContent;
import com.meant.api.plugin.transport.dto.McpToolCallParams;
import com.meant.api.plugin.transport.dto.McpToolCallRequest;
import com.meant.api.plugin.transport.dto.McpToolCallResponse;
import com.meant.api.plugin.transport.dto.McpToolResult;
import com.meant.api.plugin.transport.dto.McpToolsListResponse;
import com.meant.api.plugin.transport.profile.AgentIdentity;
import com.meant.api.plugin.spi.CapabilityId;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.plugin.spi.UcpToolResponse;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class UcpMcpClient {

    private static final String JSONRPC_VERSION = "2.0";
    private static final String UCP_AGENT_META_KEY = "ucp-agent";
    private static final String UCP_AGENT_PROFILE_KEY = "profile";

    private final AgentIdentity agentIdentity;
    private final ObjectMapper objectMapper;
    private final AtomicInteger requestIds = new AtomicInteger(1);

    @Autowired
    public UcpMcpClient(AgentIdentity agentIdentity, ObjectMapper objectMapper) {
        this.agentIdentity = agentIdentity;
        this.objectMapper = objectMapper;
    }

    public UcpMcpClient(AgentIdentity agentIdentity) {
        this(agentIdentity, new ObjectMapper());
    }

    public UcpToolResponse callTool(RestClient restClient, URI endpoint, String toolName, Object arguments) {
        McpToolCallResponse response = restClient.post()
                .uri(endpoint)
                .body(request("tools/call", new McpToolCallParams(toolName, arguments)))
                .retrieve()
                .body(McpToolCallResponse.class);

        McpToolResult result = requireToolResult(response);
        if (result.isError()) {
            throw new UcpMcpException("MCP result was marked as error: " + contentText(result.content()));
        }
        return new UcpToolResponse(
                firstContentText(result.content()),
                result.structuredContent(),
                negotiatedCapabilities(result.structuredContent())
        );
    }

    public String listTools(RestClient restClient, URI endpoint) {
        McpToolsListResponse response = restClient.post()
                .uri(endpoint)
                .body(request("tools/list", null))
                .retrieve()
                .body(McpToolsListResponse.class);

        requireNoError(response);
        if (response.result() == null) {
            throw new UcpMcpException("MCP tools/list result was missing");
        }
        try {
            return objectMapper.writeValueAsString(response.result());
        } catch (JacksonException exception) {
            throw new UcpMcpException("MCP tools/list result could not be serialized", exception);
        }
    }

    private McpToolResult requireToolResult(McpToolCallResponse response) {
        requireNoError(response);
        if (response.result() == null) {
            throw new UcpMcpException("MCP result was missing");
        }
        return response.result();
    }

    private void requireNoError(McpToolCallResponse response) {
        if (response == null) {
            throw new UcpMcpException("MCP response was empty");
        }
        if (response.error() != null) {
            throw new UcpMcpException("MCP error: " + response.error().message());
        }
    }

    private void requireNoError(McpToolsListResponse response) {
        if (response == null) {
            throw new UcpMcpException("MCP response was empty");
        }
        if (response.error() != null) {
            throw new UcpMcpException("MCP error: " + response.error().message());
        }
    }

    private McpToolCallRequest request(String method, McpToolCallParams params) {
        return new McpToolCallRequest(
                JSONRPC_VERSION,
                requestIds.getAndIncrement(),
                method,
                params,
                Map.of(UCP_AGENT_META_KEY, Map.of(UCP_AGENT_PROFILE_KEY, agentIdentity.profileUrl().toString()))
        );
    }

    private NegotiatedCapabilities negotiatedCapabilities(Object structuredContent) {
        Object capabilities = mapValue(mapValue(structuredContent, "ucp"), "capabilities");
        if (capabilities == null) {
            return NegotiatedCapabilities.none();
        }

        Map<CapabilityId, String> versions = new LinkedHashMap<>();
        if (capabilities instanceof Map<?, ?> capabilityMap) {
            capabilityMap.forEach((id, value) -> addCapability(versions, id, capabilityVersion(value)));
        } else if (capabilities instanceof Iterable<?> capabilityList) {
            for (Object capability : capabilityList) {
                if (capability instanceof Map<?, ?> capabilityFields) {
                    addCapability(
                            versions,
                            firstMapValue(capabilityFields, "id", "capability", "name"),
                            text(firstMapValue(capabilityFields, "version", "ucp_version"))
                    );
                } else {
                    addCapability(versions, capability, "");
                }
            }
        } else {
            addCapability(versions, capabilities, "");
        }
        return NegotiatedCapabilities.of(versions);
    }

    private String capabilityVersion(Object value) {
        if (value instanceof Map<?, ?> fields) {
            return text(firstMapValue(fields, "version", "ucp_version"));
        }
        return text(value);
    }

    private void addCapability(Map<CapabilityId, String> versions, Object id, String version) {
        String capabilityId = text(id);
        if (capabilityId.isBlank()) {
            return;
        }
        versions.put(CapabilityId.of(capabilityId), version == null ? "" : version);
    }

    private Object firstMapValue(Map<?, ?> values, String... keys) {
        for (String key : keys) {
            Object value = values.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Object mapValue(Object value, String key) {
        return value instanceof Map<?, ?> map ? map.get(key) : null;
    }

    private String contentText(List<McpContent> content) {
        String text = firstContentText(content);
        return text == null ? "" : text;
    }

    private String firstContentText(List<McpContent> content) {
        if (content == null) {
            return null;
        }
        return content.stream()
                .filter(item -> "text".equals(item.type()))
                .map(McpContent::text)
                .filter(text -> text != null && !text.isBlank())
                .findFirst()
                .orElse(null);
    }

    private String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}
