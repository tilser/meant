package com.meant.api.plugin.transport.client;

import com.meant.api.plugin.transport.dto.McpToolCallRequest;
import com.meant.api.plugin.transport.dto.McpToolCallResponse;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@Slf4j
@RequiredArgsConstructor
public class UcpMcpWireLogger {

    private static final int MAX_WIRE_LOG_CHARS = 65_536;
    private static final int MAX_ERROR_SUMMARY_CHARS = 2_048;
    private static final Pattern SENSITIVE_QUERY_PARAMETER = Pattern.compile(
            "(?i)([?&](?:key|token|access_token|signature|sig|secret)=)[^&#\\\"]+"
    );
    private static final Pattern SENSITIVE_ASSIGNMENT = Pattern.compile(
            "(?i)(\\b(?:authorization|access[_-]?token|client[_-]?secret|credential|api[_-]?key|token)"
                    + "\\b\\s*[:=]\\s*)([^\\s,;}&]+)"
    );
    private static final Pattern BEARER_TOKEN = Pattern.compile("(?i)(\\bbearer\\s+)[A-Za-z0-9._~+/=-]+");

    private final ObjectMapper objectMapper;
    private final UcpMcpDiagnosticsProperties diagnosticsProperties;

    static UcpMcpWireLogger disabled(ObjectMapper objectMapper) {
        return new UcpMcpWireLogger(objectMapper, new UcpMcpDiagnosticsProperties(false));
    }

    void logRequest(URI endpoint, String toolName, McpToolCallRequest request) {
        if (!enabled(toolName)) {
            return;
        }
        log.info(
                "UCP checkout MCP wire request endpoint={} tool={} payload={}",
                endpoint,
                toolName,
                wireJson(request)
        );
    }

    void logResponse(URI endpoint, String toolName, McpToolCallResponse response) {
        if (!enabled(toolName)) {
            return;
        }
        log.info(
                "UCP checkout MCP wire response endpoint={} tool={} status=200 payload={}",
                endpoint,
                toolName,
                wireJson(response)
        );
    }

    void logHttpFailure(URI endpoint, String toolName, RestClientResponseException exception) {
        if (!enabled(toolName)) {
            return;
        }
        log.warn(
                "UCP checkout MCP wire response endpoint={} tool={} status={} payload={}",
                endpoint,
                toolName,
                exception.getStatusCode().value(),
                wireText(exception.getResponseBodyAsString())
        );
    }

    void logUpstreamHttpFailure(URI endpoint, String toolName, RestClientResponseException exception) {
        if (!exception.getStatusCode().is4xxClientError()) {
            return;
        }
        UpstreamErrorSummary summary = upstreamErrorSummary(exception.getResponseBodyAsString());
        if (!summary.hasDetails()) {
            return;
        }
        log.warn(
                "UCP MCP upstream client error endpoint={} tool={} status={} code={} content={}",
                sanitizeSensitiveText(endpoint.toString()),
                toolName,
                exception.getStatusCode().value(),
                summary.code(),
                summary.content()
        );
    }

    private boolean enabled(String toolName) {
        return diagnosticsProperties.checkoutWireLoggingEnabled()
                && toolName != null
                && (toolName.contains("cart") || toolName.contains("checkout"));
    }

    private String wireJson(Object value) {
        try {
            Object normalized = objectMapper.readValue(objectMapper.writeValueAsString(value), Object.class);
            return truncate(objectMapper.writeValueAsString(sanitize(null, normalized)));
        } catch (JacksonException | IllegalArgumentException exception) {
            return "<serialization-failed:" + exception.getClass().getSimpleName() + ">";
        }
    }

    private String wireText(String value) {
        if (value == null || value.isBlank()) {
            return "<empty>";
        }
        try {
            Object parsed = objectMapper.readValue(value, Object.class);
            return truncate(objectMapper.writeValueAsString(sanitize(null, parsed)));
        } catch (JacksonException exception) {
            return truncate(sanitizeSensitiveText(value));
        }
    }

    private Object sanitize(String key, Object value) {
        if (sensitiveKey(key)) {
            return "<redacted>";
        }
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            source.forEach((mapKey, mapValue) -> {
                if (mapKey != null) {
                    String field = mapKey.toString();
                    sanitized.put(field, sanitize(field, mapValue));
                }
            });
            return sanitized;
        }
        if (value instanceof Iterable<?> source) {
            List<Object> sanitized = new ArrayList<>();
            source.forEach(item -> sanitized.add(sanitize(null, item)));
            return sanitized;
        }
        if ("text".equals(key) && value instanceof String textValue) {
            return sanitizeEmbeddedJson(textValue);
        }
        if (value instanceof String textValue) {
            return sanitizeSensitiveText(textValue);
        }
        return value;
    }

    private Object sanitizeEmbeddedJson(String value) {
        try {
            Object parsed = objectMapper.readValue(value, Object.class);
            return objectMapper.writeValueAsString(sanitize(null, parsed));
        } catch (JacksonException exception) {
            return sanitizeSensitiveText(value);
        }
    }

    private UpstreamErrorSummary upstreamErrorSummary(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return UpstreamErrorSummary.missing();
        }
        try {
            Object parsed = objectMapper.readValue(responseBody, Object.class);
            if (!(parsed instanceof Map<?, ?> root)) {
                return UpstreamErrorSummary.missing();
            }
            Map<?, ?> error = root.get("error") instanceof Map<?, ?> nested ? nested : root;
            return new UpstreamErrorSummary(
                    summaryValue("code", error.get("code")),
                    summaryValue("content", error.get("content"))
            );
        } catch (JacksonException exception) {
            return UpstreamErrorSummary.missing();
        }
    }

    private String summaryValue(String key, Object value) {
        if (value == null) {
            return "<missing>";
        }
        try {
            return truncate(objectMapper.writeValueAsString(sanitize(key, value)), MAX_ERROR_SUMMARY_CHARS);
        } catch (JacksonException | IllegalArgumentException exception) {
            return "<unavailable>";
        }
    }

    private String sanitizeSensitiveText(String value) {
        String sanitized = sanitizeSensitiveQueryParameters(value);
        sanitized = BEARER_TOKEN.matcher(sanitized).replaceAll("$1<redacted>");
        return SENSITIVE_ASSIGNMENT.matcher(sanitized).replaceAll("$1<redacted>");
    }

    private String sanitizeSensitiveQueryParameters(String value) {
        return SENSITIVE_QUERY_PARAMETER.matcher(value).replaceAll("$1<redacted>");
    }

    private boolean sensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT).replace('-', '_');
        return normalized.contains("authorization")
                || normalized.contains("access_token")
                || normalized.contains("client_secret")
                || normalized.equals("token")
                || normalized.contains("credential")
                || normalized.contains("payment")
                || normalized.contains("instrument")
                || normalized.contains("gift_card")
                || normalized.contains("ec_auth")
                || normalized.contains("card_number")
                || normalized.contains("cvv")
                || normalized.contains("cvc")
                || normalized.contains("security_code");
    }

    private String truncate(String value) {
        return truncate(value, MAX_WIRE_LOG_CHARS);
    }

    private String truncate(String value, int maxChars) {
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars) + "<truncated>";
    }

    private record UpstreamErrorSummary(String code, String content) {

        private static UpstreamErrorSummary missing() {
            return new UpstreamErrorSummary("<missing>", "<missing>");
        }

        private boolean hasDetails() {
            return !"<missing>".equals(code) || !"<missing>".equals(content);
        }
    }
}
