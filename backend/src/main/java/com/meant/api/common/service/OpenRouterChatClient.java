package com.meant.api.common.service;

import com.meant.api.common.exception.OpenRouterException;
import com.meant.api.common.properties.OpenRouterProperties;
import com.meant.api.common.service.dto.OpenRouterChatMessage;
import com.meant.api.common.service.dto.OpenRouterChatRequest;
import com.meant.api.common.service.dto.OpenRouterChatResponse;
import com.meant.api.common.service.dto.OpenRouterJsonSchema;
import com.meant.api.common.service.dto.OpenRouterJsonSchemaDefinition;
import com.meant.api.common.service.dto.OpenRouterResponseFormat;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
@RequiredArgsConstructor
public class OpenRouterChatClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestClient.Builder restClientBuilder;
    private final OpenRouterProperties openRouterProperties;

    public String completeJson(
            String model,
            String systemPrompt,
            String userPrompt,
            String schemaName,
            OpenRouterJsonSchemaDefinition schema
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
                )
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
        } catch (RestClientException exception) {
            throw new OpenRouterException("Failed to fetch OpenRouter chat completion", exception);
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
                            throw new OpenRouterException("OpenRouter stream failed: " + response.getStatusCode());
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
            throw new OpenRouterException("Failed to fetch OpenRouter chat stream", exception);
        }
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
        } catch (JsonProcessingException exception) {
            throw new OpenRouterException("OpenRouter returned invalid stream JSON", exception);
        }
    }
}
