package com.meant.api.common.service;

import com.meant.api.common.exception.OpenRouterException;
import com.meant.api.common.properties.OpenRouterProperties;
import com.meant.api.common.service.dto.OpenRouterChatMessage;
import com.meant.api.common.service.dto.OpenRouterChatRequest;
import com.meant.api.common.service.dto.OpenRouterChatResponse;
import com.meant.api.common.service.dto.OpenRouterJsonSchema;
import com.meant.api.common.service.dto.OpenRouterJsonSchemaDefinition;
import com.meant.api.common.service.dto.OpenRouterResponseFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
@RequiredArgsConstructor
public class OpenRouterChatClient {

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
