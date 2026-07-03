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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenRouterChatClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
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

    public void streamText(
            String model,
            List<OpenRouterChatMessage> messages,
            Consumer<String> chunkConsumer
    ) {
        if (openRouterProperties.apiKey().isBlank()) {
            throw new OpenRouterException("OpenRouter API key is not configured");
        }

        OpenRouterChatRequest request = new OpenRouterChatRequest(
                model,
                messages,
                0.3,
                null,
                true
        );

        try {
            restClientBuilder.clone()
                    .baseUrl(openRouterProperties.baseUrl())
                    .build()
                    .post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + openRouterProperties.apiKey())
                    .header("X-Title", openRouterProperties.appTitle())
                    .body(request)
                    .exchange((httpRequest, response) -> {
                        if (response.getStatusCode().isError()) {
                            throw httpFailure(
                                    "chat stream",
                                    model,
                                    response.getStatusCode(),
                                    responseBody(response),
                                    null
                            );
                        }
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                                response.getBody(),
                                StandardCharsets.UTF_8
                        ))) {
                            streamChunks(reader, chunkConsumer);
                        } catch (IOException exception) {
                            throw new OpenRouterException("Failed to read OpenRouter stream", exception);
                        }
                        return null;
                    });
        } catch (RestClientException exception) {
            throw transportFailure("chat stream", model, exception);
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

    private String responseBody(ClientHttpResponse response) {
        try {
            return new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return "Could not read OpenRouter error response body: " + exception.getMessage();
        }
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

    private void streamChunks(
            BufferedReader reader,
            Consumer<String> chunkConsumer
    ) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.startsWith("data:")) {
                continue;
            }
            String data = line.substring("data:".length()).trim();
            if (data.isBlank() || "[DONE]".equals(data)) {
                if ("[DONE]".equals(data)) {
                    break;
                }
                continue;
            }
            String chunk = streamContent(data);
            if (!chunk.isBlank()) {
                chunkConsumer.accept(chunk);
            }
        }
    }

    private String streamContent(String data) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(data);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return "";
            }
            JsonNode content = choices.get(0).path("delta").path("content");
            return content.isTextual() ? content.asText() : "";
        } catch (JacksonException exception) {
            throw new OpenRouterException("OpenRouter returned invalid stream JSON", exception);
        }
    }
}
