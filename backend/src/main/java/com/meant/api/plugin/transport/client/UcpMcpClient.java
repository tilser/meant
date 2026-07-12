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
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
@Slf4j
public class UcpMcpClient {

    private static final String JSONRPC_VERSION = "2.0";
    private static final String UCP_AGENT_META_KEY = "ucp-agent";
    private static final String UCP_AGENT_PROFILE_KEY = "profile";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String IDEMPOTENCY_KEY_META_KEY = "idempotency-key";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final AgentIdentity agentIdentity;
    private final ObjectMapper objectMapper;
    private final UcpMcpWireLogger wireLogger;
    private final AtomicInteger requestIds = new AtomicInteger(1);

    @Autowired
    public UcpMcpClient(
            AgentIdentity agentIdentity,
            ObjectMapper objectMapper,
            UcpMcpWireLogger wireLogger
    ) {
        this.agentIdentity = agentIdentity;
        this.objectMapper = objectMapper;
        this.wireLogger = wireLogger;
    }

    public UcpMcpClient(AgentIdentity agentIdentity, ObjectMapper objectMapper) {
        this(agentIdentity, objectMapper, UcpMcpWireLogger.disabled(objectMapper));
    }

    public UcpMcpClient(AgentIdentity agentIdentity) {
        this(agentIdentity, new ObjectMapper());
    }

    public UcpToolResponse callTool(RestClient restClient, URI endpoint, String toolName, Object arguments) {
        return callTool(restClient, endpoint, toolName, arguments, Map.of());
    }

    public UcpToolResponse callTool(
            RestClient restClient,
            URI endpoint,
            String toolName,
            Object arguments,
            Map<String, String> headers
    ) {
        return callTool(restClient, endpoint, toolName, arguments, headers, false);
    }

    public UcpToolResponse callToolAllowingJsonToolErrors(
            RestClient restClient,
            URI endpoint,
            String toolName,
            Object arguments,
            Map<String, String> headers
    ) {
        return callTool(restClient, endpoint, toolName, arguments, headers, true);
    }

    public UcpToolResponse callToolAuthenticatedAllowingJsonToolErrors(
            RestClient restClient,
            URI endpoint,
            String toolName,
            Object arguments,
            Map<String, String> headers,
            Consumer<HttpHeaders> authentication
    ) {
        return callTool(restClient, endpoint, toolName, arguments, headers, authentication, true);
    }

    private UcpToolResponse callTool(
            RestClient restClient,
            URI endpoint,
            String toolName,
            Object arguments,
            Map<String, String> headers,
            boolean allowJsonToolErrors
    ) {
        return callTool(restClient, endpoint, toolName, arguments, headers, ignored -> { }, allowJsonToolErrors);
    }

    private UcpToolResponse callTool(
            RestClient restClient,
            URI endpoint,
            String toolName,
            Object arguments,
            Map<String, String> headers,
            Consumer<HttpHeaders> authentication,
            boolean allowJsonToolErrors
    ) {
        McpToolCallRequest request = request(
                "tools/call",
                new McpToolCallParams(toolName, argumentsWithAgentMeta(arguments, headers))
        );
        wireLogger.logRequest(endpoint, toolName, request);
        McpToolCallResponse response;
        try {
            response = restClient.post()
                    .uri(endpoint)
                    .headers(httpHeaders -> {
                        if (headers != null) {
                            headers.forEach(httpHeaders::set);
                        }
                        authentication.accept(httpHeaders);
                    })
                    .body(request)
                    .retrieve()
                    .body(McpToolCallResponse.class);
        } catch (RestClientResponseException exception) {
            wireLogger.logHttpFailure(endpoint, toolName, exception);
            throw exception;
        }
        logMerchantToolExchange(toolName, response, allowJsonToolErrors);
        wireLogger.logResponse(endpoint, toolName, response);

        McpToolResult result = requireToolResult(response);
        String textContent = firstContentText(result.content());
        if (result.isError()) {
            boolean hasJsonTextPayload = hasJsonTextPayload(textContent);
            boolean canReturnToolError = allowJsonToolErrors
                    && (hasJsonTextPayload || hasResultStructuredContent(result.structuredContent()));
            if (!canReturnToolError) {
                throw new UcpMcpException("MCP tool result was marked as error");
            }
            if (!hasJsonTextPayload) {
                textContent = null;
            }
        }
        return new UcpToolResponse(
                textContent,
                result.structuredContent(),
                negotiatedCapabilities(result.structuredContent())
        );
    }

    private void logMerchantToolExchange(
            String toolName,
            McpToolCallResponse response,
            boolean allowJsonToolErrors
    ) {
        if (!isCartOrCheckoutTool(toolName)) {
            return;
        }
        McpToolResult result = response == null ? null : response.result();
        String outcome = exchangeOutcome(response, result, allowJsonToolErrors);
        boolean failed = !"success".equals(outcome) && !"business_response".equals(outcome);
        String message = "UCP merchant tool exchange tool={} outcome={} textPresent={} structuredPresent={}";
        Object[] values = {
                toolName,
                outcome,
                result != null && firstContentText(result.content()) != null,
                result != null && result.structuredContent() != null
        };
        if (failed) {
            log.warn(message, values);
        } else {
            log.info(message, values);
        }
    }

    private String exchangeOutcome(
            McpToolCallResponse response,
            McpToolResult result,
            boolean allowJsonToolErrors
    ) {
        if (response == null) {
            return "empty_response";
        }
        if (response.error() != null) {
            return "json_rpc_error";
        }
        if (result == null) {
            return "missing_result";
        }
        if (!result.isError()) {
            return "success";
        }
        String textContent = firstContentText(result.content());
        boolean isTypedBusinessResponse = allowJsonToolErrors
                && (hasJsonTextPayload(textContent)
                        || hasResultStructuredContent(result.structuredContent()));
        return isTypedBusinessResponse ? "business_response" : "tool_error";
    }

    private boolean isCartOrCheckoutTool(String toolName) {
        if (toolName == null) {
            return false;
        }
        return toolName.contains("cart") || toolName.contains("checkout");
    }

    public String listTools(RestClient restClient, URI endpoint) {
        McpToolsListResponse response = restClient.post()
                .uri(endpoint)
                .body(request("tools/list", new McpToolCallParams(null, argumentsWithAgentMeta(null, Map.of()))))
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
            throw new UcpMcpRemoteErrorException("MCP JSON-RPC response contained an error");
        }
    }

    private void requireNoError(McpToolsListResponse response) {
        if (response == null) {
            throw new UcpMcpException("MCP response was empty");
        }
        if (response.error() != null) {
            throw new UcpMcpException("MCP tools/list JSON-RPC response contained an error");
        }
    }

    private McpToolCallRequest request(String method, McpToolCallParams params) {
        return new McpToolCallRequest(
                JSONRPC_VERSION,
                requestIds.getAndIncrement(),
                method,
                params
        );
    }

    private Map<String, Object> argumentsWithAgentMeta(Object arguments, Map<String, String> headers) {
        Map<String, Object> values = objectMap(arguments);
        Map<String, Object> meta = mapValue(values.get("meta"));
        Map<String, Object> ucpAgent = mapValue(meta.get(UCP_AGENT_META_KEY));

        ucpAgent.put(UCP_AGENT_PROFILE_KEY, agentIdentity.profileUrl().toString());
        meta.put(UCP_AGENT_META_KEY, ucpAgent);
        String idempotencyKey = firstHeader(headers, IDEMPOTENCY_KEY_HEADER);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            meta.put(IDEMPOTENCY_KEY_META_KEY, idempotencyKey.trim());
        }
        values.put("meta", meta);

        return values;
    }

    private String firstHeader(Map<String, String> headers, String expectedHeader) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(expectedHeader)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Map<String, Object> objectMap(Object value) {
        if (value == null) {
            return new LinkedHashMap<>();
        }
        if (value instanceof Map<?, ?> map) {
            return stringKeyMap(map);
        }
        try {
            Map<String, Object> converted = objectMapper.readValue(objectMapper.writeValueAsString(value), MAP_TYPE);
            return converted == null ? new LinkedHashMap<>() : new LinkedHashMap<>(converted);
        } catch (IllegalArgumentException | JacksonException exception) {
            throw new UcpMcpException("MCP tool arguments could not be serialized", exception);
        }
    }

    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? stringKeyMap(map) : new LinkedHashMap<>();
    }

    private Map<String, Object> stringKeyMap(Map<?, ?> source) {
        Map<String, Object> values = new LinkedHashMap<>();
        source.forEach((key, mapValue) -> {
            if (key != null) {
                values.put(key.toString(), mapValue);
            }
        });
        return values;
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
        if (value instanceof Iterable<?> versions) {
            for (Object version : versions) {
                String resolved = capabilityVersion(version);
                if (!resolved.isBlank()) {
                    return resolved;
                }
            }
            return "";
        }
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

    private boolean hasJsonTextPayload(String textContent) {
        if (textContent == null) {
            return false;
        }
        String trimmed = textContent.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    private boolean hasResultStructuredContent(Object structuredContent) {
        if (structuredContent instanceof Map<?, ?> map) {
            return map.keySet().stream()
                    .map(Object::toString)
                    .anyMatch(key -> !"ucp".equals(key));
        }
        return structuredContent != null;
    }

    private String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

}
