package com.meant.api.module.discount.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meant.api.common.exception.OpenRouterException;
import com.meant.api.common.service.OpenRouterChatClient;
import com.meant.api.common.service.OpenRouterJsonExtractor;
import com.meant.api.common.service.dto.OpenRouterJsonSchemaDefinition;
import com.meant.api.common.service.dto.OpenRouterPlugin;
import com.meant.api.module.discount.exception.DiscountCodeException;
import com.meant.api.module.discount.properties.DiscountCodeSearchProperties;
import com.meant.api.module.discount.service.command.SearchDiscountCodesCommand;
import com.meant.api.module.discount.service.dto.DiscountCodeCandidateSource;
import com.meant.api.module.discount.service.dto.DiscountMerchant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Validated
public class DiscountCodeWebSearchService {

    private static final String SYSTEM_PROMPT = """
            You find real merchant coupon, discount, and promo code candidates from current web search results.
            Use the web search plugin results as the only source of truth.
            Do not invent codes, titles, descriptions, restrictions, sources, or dates.
            Prefer official merchant pages and reliable coupon or deal pages.
            Return only codes that appear in web search results.
            If no codes are found, return an empty codes array.
            For every candidate include the exact code, title, description, source_url, confidence from 0 to 1,
            restrictions, valid_from_text if visible, and valid_until_text if visible.
            Return structured JSON only.
            """;

    private final OpenRouterChatClient openRouterChatClient;
    private final DiscountCodeSearchProperties properties;
    private final DiscountCodeCandidateNormalizationService normalizationService;
    private final ObjectMapper objectMapper;

    public List<DiscountCodeCandidateSource> search(
            DiscountMerchant merchant,
            @NotNull @Valid SearchDiscountCodesCommand command,
            Instant now
    ) {
        try {
            String response = openRouterChatClient.completeJson(
                    properties.openRouterModel(),
                    SYSTEM_PROMPT,
                    userPrompt(merchant, command, now),
                    "discount_code_search",
                    responseSchema(),
                    List.of(new OpenRouterPlugin("web", properties.webMaxResults()))
            );
            DiscountCodeWebSearchResponse parsed = parseResponse(response);
            return normalizationService.normalize(
                    parsed.codes().stream()
                            .filter(candidate -> candidate != null)
                            .map(DiscountCodeWebSearchService::toCandidate)
                            .toList(),
                    properties.maxCandidates()
            );
        } catch (OpenRouterException | JacksonException exception) {
            throw DiscountCodeException.upstream("Discount code web search failed", exception);
        }
    }

    private String userPrompt(
            DiscountMerchant merchant,
            SearchDiscountCodesCommand command,
            Instant now
    ) throws JacksonException {
        return """
                Merchant domain: %s
                Merchant name: %s
                Current date: %s
                Cart item context for validation only:
                %s

                Find up to %d candidate codes for this merchant. Return an empty codes array when none are visible.
                """.formatted(
                merchant.domain(),
                merchant.name() == null || merchant.name().isBlank() ? "Unknown" : merchant.name(),
                now.atZone(ZoneOffset.UTC).toLocalDate(),
                objectMapper.writeValueAsString(command.items()),
                properties.maxCandidates()
        );
    }

    private OpenRouterJsonSchemaDefinition responseSchema() {
        OpenRouterJsonSchemaDefinition candidateSchema = OpenRouterJsonSchemaDefinition.object(
                List.of(
                        "code",
                        "title",
                        "description",
                        "source_url",
                        "confidence",
                        "restrictions",
                        "valid_from_text",
                        "valid_until_text"
                ),
                Map.of(
                        "code", OpenRouterJsonSchemaDefinition.string(),
                        "title", OpenRouterJsonSchemaDefinition.string(),
                        "description", OpenRouterJsonSchemaDefinition.string(),
                        "source_url", OpenRouterJsonSchemaDefinition.string(),
                        "confidence", OpenRouterJsonSchemaDefinition.number(),
                        "restrictions", OpenRouterJsonSchemaDefinition.string(),
                        "valid_from_text", OpenRouterJsonSchemaDefinition.string(),
                        "valid_until_text", OpenRouterJsonSchemaDefinition.string()
                )
        );
        return OpenRouterJsonSchemaDefinition.object(
                List.of("codes"),
                Map.of(
                        "codes",
                        OpenRouterJsonSchemaDefinition.array(candidateSchema, 0, properties.maxCandidates())
                )
        );
    }

    private DiscountCodeWebSearchResponse parseResponse(String response) throws JacksonException {
        DiscountCodeWebSearchResponse parsed = objectMapper.readValue(
                OpenRouterJsonExtractor.objectCandidate(response),
                DiscountCodeWebSearchResponse.class
        );
        if (parsed == null || parsed.codes() == null) {
            return new DiscountCodeWebSearchResponse(List.of());
        }
        return parsed;
    }

    private static DiscountCodeCandidateSource toCandidate(DiscountCodeWebCandidate candidate) {
        return new DiscountCodeCandidateSource(
                candidate.code(),
                candidate.title(),
                candidate.description(),
                candidate.sourceUrl(),
                candidate.confidence(),
                candidate.restrictions(),
                candidate.validFromText(),
                candidate.validUntilText()
        );
    }

    private record DiscountCodeWebSearchResponse(List<DiscountCodeWebCandidate> codes) {
    }

    private record DiscountCodeWebCandidate(
            String code,
            String title,
            String description,
            @JsonProperty("source_url")
            String sourceUrl,
            Double confidence,
            String restrictions,
            @JsonProperty("valid_from_text")
            String validFromText,
            @JsonProperty("valid_until_text")
            String validUntilText
    ) {
    }
}
