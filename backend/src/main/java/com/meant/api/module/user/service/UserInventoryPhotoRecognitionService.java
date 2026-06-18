package com.meant.api.module.user.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.meant.api.common.properties.OpenRouterProperties;
import com.meant.api.common.service.dto.OpenRouterChatMessage;
import com.meant.api.common.service.dto.OpenRouterChatResponse;
import com.meant.api.common.service.dto.OpenRouterJsonSchema;
import com.meant.api.common.service.dto.OpenRouterJsonSchemaDefinition;
import com.meant.api.common.service.dto.OpenRouterResponseFormat;
import com.meant.api.module.user.constant.UserInventoryCategory;
import com.meant.api.module.user.service.command.CreateUserInventoryPhotoItemCommand;
import com.meant.api.module.user.service.dto.UserInventoryPhotoRecognitionResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserInventoryPhotoRecognitionService {

    private static final String SYSTEM_PROMPT = """
            You identify one owned household item from a user's inventory photo for Meant.
            Return conservative facts only. If a brand is not visible, leave it empty.
            Choose category APPAREL for clothing, shoes, and accessories; PANTRY for food, grocery, and consumables;
            HOME for home goods, kitchen, cleaning, decor, and appliances; OTHER when uncertain.
            Attributes should be short descriptors visible in the photo, not shopping recommendations.
            """;

    private final RestClient.Builder restClientBuilder;
    private final OpenRouterProperties openRouterProperties;
    private final ObjectMapper objectMapper;

    public Optional<UserInventoryPhotoRecognitionResult> recognize(CreateUserInventoryPhotoItemCommand command) {
        if (openRouterProperties.apiKey().isBlank()) {
            return Optional.empty();
        }

        VisionChatRequest request = new VisionChatRequest(
                openRouterProperties.models().productRecommendationExplainer(),
                List.of(
                        new VisionChatMessage("system", SYSTEM_PROMPT),
                        new VisionChatMessage("user", List.of(
                                VisionContent.text(userPrompt(command)),
                                VisionContent.image(command.photoUrl())
                        ))
                ),
                0.0,
                new OpenRouterResponseFormat(
                        "json_schema",
                        new OpenRouterJsonSchema("inventory_photo_recognition", true, responseSchema())
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
            return Optional.of(toResult(content(response)));
        } catch (JacksonException exception) {
            log.warn("Could not parse inventory photo recognition JSON: {}", exception.getMessage());
            return Optional.empty();
        } catch (RuntimeException exception) {
            log.warn("Could not recognize inventory photo: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    private String userPrompt(CreateUserInventoryPhotoItemCommand command) {
        return """
                Use the photo to identify the owned item.
                User-provided fallback name: %s
                User-provided fallback brand: %s
                User-provided fallback category: %s
                User notes: %s
                """.formatted(
                value(command.name()),
                value(command.brand()),
                command.category() == null ? "" : command.category().name(),
                value(command.notes())
        );
    }

    private OpenRouterJsonSchemaDefinition responseSchema() {
        return OpenRouterJsonSchemaDefinition.object(
                List.of("name", "category", "attributes", "consumable"),
                Map.of(
                        "name", OpenRouterJsonSchemaDefinition.string(),
                        "brand", OpenRouterJsonSchemaDefinition.string(),
                        "category", OpenRouterJsonSchemaDefinition.stringEnum(List.of(
                                UserInventoryCategory.APPAREL.name(),
                                UserInventoryCategory.PANTRY.name(),
                                UserInventoryCategory.HOME.name(),
                                UserInventoryCategory.OTHER.name()
                        )),
                        "description", OpenRouterJsonSchemaDefinition.string(),
                        "attributes", OpenRouterJsonSchemaDefinition.array(OpenRouterJsonSchemaDefinition.string()),
                        "consumable", OpenRouterJsonSchemaDefinition.bool()
                )
        );
    }

    private UserInventoryPhotoRecognitionResult toResult(String content) throws JacksonException {
        RecognitionResponse response = objectMapper.readValue(content, RecognitionResponse.class);
        if (response == null) {
            return new UserInventoryPhotoRecognitionResult(null, null, UserInventoryCategory.OTHER, null, List.of(), null);
        }
        return new UserInventoryPhotoRecognitionResult(
                blankToNull(response.name()),
                blankToNull(response.brand()),
                UserInventoryCategory.fromValue(response.category()),
                blankToNull(response.description()),
                response.attributes() == null ? List.of() : response.attributes().stream()
                        .filter(attribute -> attribute != null && !attribute.isBlank())
                        .map(String::trim)
                        .distinct()
                        .limit(8)
                        .toList(),
                response.consumable()
        );
    }

    private String content(OpenRouterChatResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            return "{}";
        }
        OpenRouterChatMessage message = response.choices().getFirst().message();
        if (message == null || message.content() == null || message.content().isBlank()) {
            return "{}";
        }
        return message.content();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record VisionChatRequest(
            String model,
            List<VisionChatMessage> messages,
            Double temperature,
            @JsonProperty("response_format")
            OpenRouterResponseFormat responseFormat
    ) {
    }

    private record VisionChatMessage(
            String role,
            Object content
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record VisionContent(
            String type,
            String text,
            @JsonProperty("image_url")
            VisionImage imageUrl
    ) {

        static VisionContent text(String text) {
            return new VisionContent("text", text, null);
        }

        static VisionContent image(String imageUrl) {
            return new VisionContent("image_url", null, new VisionImage(imageUrl));
        }
    }

    private record VisionImage(
            String url
    ) {
    }

    private record RecognitionResponse(
            String name,
            String brand,
            String category,
            String description,
            List<String> attributes,
            Boolean consumable
    ) {
    }
}
