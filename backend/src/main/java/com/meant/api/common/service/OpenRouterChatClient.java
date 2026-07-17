package com.meant.api.common.service;

import com.meant.api.common.exception.OpenRouterException;
import com.meant.api.common.properties.OpenRouterProperties;
import com.meant.api.common.service.dto.OpenRouterChatMessage;
import com.meant.api.common.service.dto.OpenRouterChatRequest;
import com.meant.api.common.service.dto.OpenRouterChatResponse;
import com.meant.api.common.service.dto.OpenRouterJsonSchema;
import com.meant.api.common.service.dto.OpenRouterJsonSchemaDefinition;
import com.meant.api.common.service.dto.OpenRouterPlugin;
import com.meant.api.common.service.dto.OpenRouterResponseFormat;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenRouterChatClient {

    private static final int RESPONSE_BODY_PREVIEW_LIMIT = 600;
    private static final Pattern SPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile(
            "(?i)(bearer\\s+)[A-Za-z0-9._~+/=-]+"
    );
    private static final Pattern SECRET_JSON_FIELD_PATTERN = Pattern.compile(
            "(?i)(\"(?:api[_-]?key|authorization|token|secret)\"\\s*:\\s*\")[^\"]+(\")"
    );

    private final RestClient.Builder restClientBuilder;
    private final OpenRouterProperties openRouterProperties;

    public String completeJson(
            String model,
            String systemPrompt,
            String userPrompt,
            String schemaName,
            OpenRouterJsonSchemaDefinition schema
    ) {
        return completeJson(model, systemPrompt, userPrompt, schemaName, schema, null);
    }

    public String completeJson(
            String model,
            String systemPrompt,
            String userPrompt,
            String schemaName,
            OpenRouterJsonSchemaDefinition schema,
            List<OpenRouterPlugin> plugins
    ) {
        if (openRouterProperties.apiKey().isBlank()) {
            throw new OpenRouterException("OpenRouter API key is not configured");
        }

        OpenRouterChatRequest request = new OpenRouterChatRequest(
                model,
                List.of(
                        new OpenRouterChatMessage("system", systemPrompt),
                        new OpenRouterChatMessage("user", userPrompt)
                ),
                0.0,
                new OpenRouterResponseFormat(
                        "json_schema",
                        new OpenRouterJsonSchema(schemaName, true, schema)
                ),
                plugins
        );

        try {
            OpenRouterChatResponse response = restClientBuilder.clone()
                    .baseUrl(openRouterProperties.baseUrl())
                    .build()
                    .post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + openRouterProperties.apiKey())
                    .header("X-Title", openRouterProperties.appTitle())
                    .body(request)
                    .retrieve()
                    .body(OpenRouterChatResponse.class);
            return content(response);
        } catch (RestClientResponseException exception) {
            throw httpFailure(
                    "chat completion",
                    model,
                    exception.getStatusCode(),
                    exception.getResponseBodyAsString(),
                    exception.getClass().getSimpleName()
            );
        } catch (RestClientException exception) {
            throw transportFailure("chat completion", model, exception);
        }
    }

    private OpenRouterException httpFailure(
            String operation,
            String model,
            HttpStatusCode status,
            String responseBody,
            String exceptionName
    ) {
        String bodyPreview = responseBodyPreview(responseBody);
        if (exceptionName == null || exceptionName.isBlank()) {
            log.warn("OpenRouter {} failed model={} status={} responseBody={}",
                    operation, model, status.value(), bodyPreview);
            return new OpenRouterException("OpenRouter %s failed: status=%d model=%s responseBody=%s"
                    .formatted(operation, status.value(), model, bodyPreview));
        }

        log.warn("OpenRouter {} failed model={} status={} exception={} responseBody={}",
                operation, model, status.value(), exceptionName, bodyPreview);
        return new OpenRouterException("OpenRouter %s failed: status=%d model=%s exception=%s responseBody=%s"
                .formatted(operation, status.value(), model, exceptionName, bodyPreview));
    }

    private OpenRouterException transportFailure(
            String operation,
            String model,
            RestClientException exception
    ) {
        String causeMessage = responseBodyPreview(exception.getMessage());
        log.warn("OpenRouter {} transport failed model={} exception={} message={}",
                operation, model, exception.getClass().getSimpleName(), causeMessage, exception);
        return new OpenRouterException("OpenRouter %s transport failed: model=%s exception=%s message=%s"
                .formatted(operation, model, exception.getClass().getSimpleName(), causeMessage), exception);
    }

    private String responseBodyPreview(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "<empty>";
        }

        String compact = SPACE_PATTERN.matcher(responseBody).replaceAll(" ").trim();
        String redacted = compact;
        if (!openRouterProperties.apiKey().isBlank()) {
            redacted = redacted.replace(openRouterProperties.apiKey(), "[redacted-api-key]");
        }
        redacted = BEARER_TOKEN_PATTERN.matcher(redacted).replaceAll("$1[redacted-token]");
        redacted = SECRET_JSON_FIELD_PATTERN.matcher(redacted).replaceAll("$1[redacted]$2");

        if (redacted.length() <= RESPONSE_BODY_PREVIEW_LIMIT) {
            return redacted;
        }
        return redacted.substring(0, RESPONSE_BODY_PREVIEW_LIMIT) + "...";
    }

    private String content(OpenRouterChatResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new OpenRouterException("OpenRouter response did not include any choices");
        }
        OpenRouterChatMessage message = response.choices().getFirst().message();
        if (message == null || message.content() == null || message.content().isBlank()) {
            throw new OpenRouterException("OpenRouter response did not include message content");
        }
        return message.content();
    }
}
