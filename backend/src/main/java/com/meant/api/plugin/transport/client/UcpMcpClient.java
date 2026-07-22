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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
@Slf4j
public class UcpMcpClient {

    private static final String JSONRPC_VERSION = "2.0";
    private static final String UCP_AGENT_META_KEY = "ucp-agent";
    private static final String UCP_AGENT_PROFILE_KEY = "profile";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String IDEMPOTENCY_KEY_META_KEY = "idempotency-key";

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
            wireLogger.logUpstreamHttpFailure(endpoint, toolName, exception);
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

    private ObjectNode argumentsWithAgentMeta(Object arguments, Map<String, String> headers) {
        ObjectNode values = objectNode(arguments, "MCP tool arguments");
        ObjectNode meta = childObject(values.get("meta"));
        ObjectNode ucpAgent = childObject(meta.get(UCP_AGENT_META_KEY));

        ucpAgent.put(UCP_AGENT_PROFILE_KEY, agentIdentity.profileUrl().toString());
        meta.set(UCP_AGENT_META_KEY, ucpAgent);
        String idempotencyKey = firstHeader(headers, IDEMPOTENCY_KEY_HEADER);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            meta.put(IDEMPOTENCY_KEY_META_KEY, idempotencyKey.trim());
        }
        values.set("meta", meta);

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

    private ObjectNode objectNode(Object value, String context) {
        JsonNode node;
        try {
            node = value == null
                    ? objectMapper.createObjectNode()
                    : value instanceof JsonNode jsonNode ? jsonNode : objectMapper.valueToTree(value);
        } catch (IllegalArgumentException exception) {
            throw new UcpMcpException(context + " could not be serialized", exception);
        }
        if (!node.isObject()) {
            throw new UcpMcpException(context + " must be a JSON object");
        }
        return node.deepCopy().asObject();
    }

    private ObjectNode childObject(JsonNode value) {
        return value != null && value.isObject()
                ? value.deepCopy().asObject()
                : objectMapper.createObjectNode();
    }

    private NegotiatedCapabilities negotiatedCapabilities(JsonNode structuredContent) {
        JsonNode capabilities = structuredContent == null
                ? null
                : structuredContent.path("ucp").get("capabilities");
        if (capabilities == null || capabilities.isNull() || capabilities.isMissingNode()) {
            return NegotiatedCapabilities.none();
        }

        Map<CapabilityId, String> versions = new LinkedHashMap<>();
        if (capabilities.isObject()) {
            capabilities.properties().forEach(entry ->
                    addCapability(versions, entry.getKey(), capabilityVersion(entry.getValue())));
        } else if (capabilities.isArray()) {
            for (JsonNode capability : capabilities.values()) {
                if (capability.isObject()) {
                    addCapability(
                            versions,
                            firstFieldText(capability, "id", "capability", "name"),
                            firstFieldText(capability, "version", "ucp_version")
                    );
                } else {
                    addCapability(versions, scalarText(capability), "");
                }
            }
        } else {
            addCapability(versions, scalarText(capabilities), "");
        }
        return NegotiatedCapabilities.of(versions);
    }

    private String capabilityVersion(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return "";
        }
        if (value.isArray()) {
            for (JsonNode version : value.values()) {
                String resolved = capabilityVersion(version);
                if (!resolved.isBlank()) {
                    return resolved;
                }
            }
            return "";
        }
        if (value.isObject()) {
            return firstFieldText(value, "version", "ucp_version");
        }
        return scalarText(value);
    }

    private void addCapability(Map<CapabilityId, String> versions, String id, String version) {
        String capabilityId = id == null ? "" : id.trim();
        if (capabilityId.isBlank()) {
            return;
        }
        versions.put(CapabilityId.of(capabilityId), version == null ? "" : version);
    }

    private String firstFieldText(JsonNode value, String... keys) {
        for (String key : keys) {
            JsonNode field = value.get(key);
            String text = scalarText(field);
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
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

    private boolean hasResultStructuredContent(JsonNode structuredContent) {
        if (structuredContent == null || structuredContent.isNull() || structuredContent.isMissingNode()) {
            return false;
        }
        if (structuredContent.isObject()) {
            return structuredContent.properties().stream()
                    .map(Map.Entry::getKey)
                    .anyMatch(key -> !"ucp".equals(key));
        }
        return true;
    }

    private String scalarText(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode() || value.isObject() || value.isArray()) {
            return "";
        }
        return value.asString().trim();
    }

}
