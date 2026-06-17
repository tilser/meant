package com.meant.api.module.user.service;

import com.meant.api.common.exception.OpenRouterException;
import com.meant.api.common.properties.OpenRouterProperties;
import com.meant.api.common.service.OpenRouterChatClient;
import com.meant.api.common.service.dto.OpenRouterJsonSchemaDefinition;
import com.meant.api.module.user.entity.ShoppingFilter;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.repository.ShoppingFilterRepository;
import com.meant.api.module.user.service.command.ParseUserPreferenceFiltersCommand;
import com.meant.api.module.user.service.dto.ParsedUserPreferenceFilters;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@Validated
@RequiredArgsConstructor
public class UserPreferenceFilterParsingService {

    private static final String SYSTEM_PROMPT = """
            You map shopping preference descriptions to Meant's canonical shopping filter IDs.
            Return only filters that are clearly implied by the user's text.
            If the user says they avoid something, map it to the matching avoidance filter when one exists.
            Do not invent filter IDs. Put unsupported or ambiguous preferences in unmappedPreferences.
            """;

    private final ShoppingFilterRepository shoppingFilterRepository;
    private final OpenRouterChatClient openRouterChatClient;
    private final OpenRouterProperties openRouterProperties;
    private final ObjectMapper objectMapper;

    public ParsedUserPreferenceFilters parse(@NotNull @Valid ParseUserPreferenceFiltersCommand command) {
        List<ShoppingFilter> filters = shoppingFilterRepository.findAllByOrderByDisplayOrderAsc();
        if (filters.isEmpty()) {
            throw new UserException("Shopping filter catalog is empty");
        }

        String response = openRouterChatClient.completeJson(
                openRouterProperties.models().preferenceFilterParser(),
                SYSTEM_PROMPT,
                userPrompt(command.description(), filters),
                "shopping_filter_mapping",
                responseSchema(filters)
        );
        return sanitize(response, filters);
    }

    private String userPrompt(String description, List<ShoppingFilter> filters) {
        return """
                Canonical filters:
                %s

                User preference description:
                %s
                """.formatted(filterCatalog(filters), description);
    }

    private String filterCatalog(List<ShoppingFilter> filters) {
        return filters.stream()
                .map(filter -> "- %s (%s, %s): %s".formatted(
                        filter.getId(),
                        filter.getLabel(),
                        filter.getCategory(),
                        filter.getDescription()))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private OpenRouterJsonSchemaDefinition responseSchema(List<ShoppingFilter> filters) {
        return OpenRouterJsonSchemaDefinition.object(
                List.of("filterIds", "unmappedPreferences"),
                Map.of(
                        "filterIds", OpenRouterJsonSchemaDefinition.array(
                                OpenRouterJsonSchemaDefinition.stringEnum(filters.stream()
                                        .map(ShoppingFilter::getId)
                                        .toList())
                        ),
                        "unmappedPreferences", OpenRouterJsonSchemaDefinition.array(
                                OpenRouterJsonSchemaDefinition.string()
                        )
                )
        );
    }

    private ParsedUserPreferenceFilters sanitize(String response, List<ShoppingFilter> filters) {
        ParsedFiltersResponse parsed = parseResponse(response);
        Set<String> validFilterIds = filters.stream()
                .map(ShoppingFilter::getId)
                .collect(java.util.stream.Collectors.toSet());
        List<String> filterIds = sanitizeFilterIds(parsed.filterIds(), validFilterIds);
        List<String> unmappedPreferences = sanitizeUnmappedPreferences(parsed.unmappedPreferences());
        return new ParsedUserPreferenceFilters(filterIds, unmappedPreferences);
    }

    private ParsedFiltersResponse parseResponse(String response) {
        try {
            return objectMapper.readValue(response, ParsedFiltersResponse.class);
        } catch (JacksonException exception) {
            throw new OpenRouterException("OpenRouter returned invalid shopping filter JSON", exception);
        }
    }

    private List<String> sanitizeFilterIds(List<String> filterIds, Set<String> validFilterIds) {
        if (filterIds == null) {
            return List.of();
        }
        LinkedHashSet<String> sanitized = new LinkedHashSet<>();
        filterIds.stream()
                .filter(id -> id != null && validFilterIds.contains(id))
                .forEach(sanitized::add);
        return List.copyOf(sanitized);
    }

    private List<String> sanitizeUnmappedPreferences(List<String> unmappedPreferences) {
        if (unmappedPreferences == null) {
            return List.of();
        }
        return unmappedPreferences.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .limit(20)
                .toList();
    }

    private record ParsedFiltersResponse(
            List<String> filterIds,
            List<String> unmappedPreferences
    ) {
    }
}
