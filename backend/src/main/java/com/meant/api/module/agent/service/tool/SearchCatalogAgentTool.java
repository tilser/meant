package com.meant.api.module.agent.service.tool;

import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.service.AgentContextProfileService;
import com.meant.api.module.agent.service.AgentJsonSupport;
import com.meant.api.module.agent.service.AgentProductReadResultService;
import com.meant.api.module.agent.service.dto.AgentArtifact;
import com.meant.api.module.agent.service.dto.AgentProductListResult;
import com.meant.api.module.agent.service.dto.AgentProductReferenceResult;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentToolExecutionResult;
import com.meant.api.module.agent.service.dto.SearchCatalogAgentToolInput;
import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryAttributeFilter;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryAttributeName;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryCondition;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryFilters;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryLocation;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryPrice;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryPriceTier;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryRating;
import com.meant.api.module.user.constant.UserProductSearchPagination;
import com.meant.api.module.user.service.UserGroupedProductSearchService;
import com.meant.api.module.user.service.command.SearchUserProductsCommand;
import com.meant.api.module.user.service.dto.UserGroupedProductSearchResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SearchCatalogAgentTool implements AgentTool {

    private static final AgentToolDescriptor DESCRIPTOR = new AgentToolDescriptor(
            "search_catalog",
            "Search and rank the grouped commerce catalog with optional documented UCP and Shopify Global Catalog filters. "
                    + "Pass only hard constraints grounded in the request or known profile; keep soft preferences in query.",
            """
            {
              "type":"object",
              "properties":{
                "query":{"type":"string","minLength":1,"maxLength":500},
                "shipsTo":{"type":"object","properties":{"country":{"type":"string","pattern":"^[A-Za-z]{2}$"},"region":{"type":"string","minLength":1,"maxLength":100},"postalCode":{"type":"string","minLength":1,"maxLength":32}},"required":["country"],"additionalProperties":false},
                "shipsFrom":{"type":"array","maxItems":20,"items":{"type":"object","properties":{"country":{"type":"string","pattern":"^[A-Za-z]{2}$"}},"required":["country"],"additionalProperties":false}},
                "price":{"type":"object","properties":{"minUsd":{"type":"number","minimum":0,"maximum":1000000},"maxUsd":{"type":"number","minimum":0,"maximum":1000000}},"additionalProperties":false},
                "conditions":{"type":"array","uniqueItems":true,"items":{"type":"string","enum":["new","secondhand"]}},
                "attributes":{"type":"array","maxItems":3,"items":{"type":"object","properties":{"name":{"type":"string","enum":["Color","Size","Target gender"]},"values":{"type":"array","minItems":1,"maxItems":20,"uniqueItems":true,"items":{"type":"string","minLength":1,"maxLength":120}}},"required":["name","values"],"additionalProperties":false}},
                "rating":{"type":"object","properties":{"variantMinimum":{"type":"number","minimum":0,"maximum":5},"variantMinimumCount":{"type":"integer","minimum":0}},"additionalProperties":false},
                "priceTiers":{"type":"array","uniqueItems":true,"items":{"type":"string","enum":["low","medium","high"]}},
                "offset":{"type":"integer","minimum":0,"maximum":99},
                "limit":{"type":"integer","minimum":1,"maximum":20}
              },
              "required":["query"],
              "additionalProperties":false
            }
            """,
            "2",
            AgentToolRisk.READ
    );

    private final AgentJsonSupport json;
    private final AgentContextProfileService profileService;
    private final AgentProductReadResultService resultService;
    private final UserGroupedProductSearchService searchService;

    @Override
    public AgentToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AgentToolExecutionResult execute(AgentToolExecutionContext context, String argumentsJson) {
        SearchCatalogAgentToolInput input = json.readArguments(argumentsJson, SearchCatalogAgentToolInput.class);
        String query = requiredQuery(input.query());
        int offset = input.offset() == null ? UserProductSearchPagination.DEFAULT_OFFSET : input.offset();
        int limit = input.limit() == null ? UserProductSearchPagination.DEFAULT_LIMIT : input.limit();
        if (offset < 0 || offset > UserProductSearchPagination.MAX_OFFSET) {
            throw AgentProductReadToolException.invalid("Offset must be between 0 and 99.");
        }
        if (limit < 1 || limit > UserProductSearchPagination.MAX_LIMIT) {
            throw AgentProductReadToolException.invalid("Limit must be between 1 and 20.");
        }
        var profile = profileService.profile(context.userId());
        SearchUserProductsCommand command = new SearchUserProductsCommand(
                context.userId(),
                query,
                context.merchantId(),
                null,
                null,
                offset,
                limit
        );
        CatalogDiscoveryFilters filters = filters(input);
        UserGroupedProductSearchResult result = filters == null
                ? searchService.search(profile, command)
                : searchService.search(profile, command, filters);
        List<String> adjustments = List.of();
        CatalogDiscoveryFilters relaxed = relaxedQualityFilters(filters);
        if (offset == 0 && result.products().isEmpty() && relaxed != null) {
            result = searchService.search(profile, command, relaxed);
            adjustments = List.of(
                    "No products matched the rating or relative price-tier thresholds, so those quality thresholds "
                            + "were relaxed. Destination, price, condition, and product attributes were preserved."
            );
        }
        return response(result, adjustments);
    }

    private AgentToolExecutionResult response(
            UserGroupedProductSearchResult result,
            List<String> searchAdjustments
    ) {
        List<CanonicalProduct> products = result.products();
        List<AgentProductReferenceResult> references = IntStream.range(0, products.size())
                .mapToObj(index -> resultService.reference(products.get(index), index + 1))
                .toList();
        List<AgentArtifact> artifacts = IntStream.range(0, products.size())
                .mapToObj(index -> resultService.discoveryArtifacts(
                        products.get(index),
                        index + 1,
                        result.productRankingExplanations().get(products.get(index).key()),
                        result.productPersonalizations().get(products.get(index).key()),
                        result.offerRankingExplanations()
                ))
                .flatMap(List::stream)
                .toList();
        AgentProductListResult output = new AgentProductListResult(
                references,
                result.nextOffset(),
                result.hasMore(),
                result.upstreamTruncated(),
                List.of(),
                null,
                searchAdjustments
        );
        String summary = "Found " + products.size() + " grounded product option(s).";
        if (!searchAdjustments.isEmpty()) {
            summary += " " + searchAdjustments.getFirst();
        }
        return AgentToolExecutionResult.read(
                json.write(output), summary, artifacts);
    }

    private CatalogDiscoveryFilters filters(SearchCatalogAgentToolInput input) {
        List<CatalogDiscoveryCondition> conditions = input.conditions().stream()
                .map(this::condition)
                .distinct()
                .toList();
        List<CatalogDiscoveryLocation> shipsFrom = input.shipsFrom().stream()
                .map(origin -> location(origin.country(), null, null, "shipsFrom"))
                .distinct()
                .toList();
        List<CatalogDiscoveryAttributeFilter> attributes = attributes(input.attributes());
        List<CatalogDiscoveryPriceTier> priceTiers = input.priceTiers().stream()
                .map(this::priceTier)
                .distinct()
                .toList();
        CatalogDiscoveryLocation shipsTo = input.shipsTo() == null
                ? null
                : location(
                        input.shipsTo().country(),
                        input.shipsTo().region(),
                        input.shipsTo().postalCode(),
                        "shipsTo"
                );
        CatalogDiscoveryPrice price = price(input.price());
        CatalogDiscoveryRating rating = rating(input.rating());
        boolean constrained = shipsTo != null
                || !shipsFrom.isEmpty()
                || price != null
                || !conditions.isEmpty()
                || !attributes.isEmpty()
                || rating != null
                || !priceTiers.isEmpty();
        return constrained
                ? new CatalogDiscoveryFilters(
                        true,
                        conditions,
                        shipsTo,
                        shipsFrom,
                        price,
                        List.of(),
                        List.of(),
                        attributes,
                        rating,
                        priceTiers
                )
                : null;
    }

    private CatalogDiscoveryFilters relaxedQualityFilters(CatalogDiscoveryFilters filters) {
        if (filters == null || filters.rating() == null && filters.priceTiers().isEmpty()) {
            return null;
        }
        return new CatalogDiscoveryFilters(
                filters.available(),
                filters.conditions(),
                filters.shipsTo(),
                filters.shipsFrom(),
                filters.price(),
                filters.shopIds(),
                filters.categoryIds(),
                filters.attributes(),
                null,
                List.of()
        );
    }

    private List<CatalogDiscoveryAttributeFilter> attributes(
            List<SearchCatalogAgentToolInput.Attribute> rawAttributes
    ) {
        List<CatalogDiscoveryAttributeFilter> attributes = new ArrayList<>();
        Set<CatalogDiscoveryAttributeName> names = new LinkedHashSet<>();
        for (SearchCatalogAgentToolInput.Attribute raw : rawAttributes) {
            if (raw == null) {
                throw AgentProductReadToolException.invalid("Catalog attributes must not contain null entries.");
            }
            CatalogDiscoveryAttributeName name = attributeName(raw.name());
            if (!names.add(name)) {
                throw AgentProductReadToolException.invalid("Each catalog attribute may be supplied only once.");
            }
            List<String> values = raw.values().stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .distinct()
                    .toList();
            if (values.isEmpty()) {
                throw AgentProductReadToolException.invalid("Each catalog attribute needs at least one value.");
            }
            attributes.add(new CatalogDiscoveryAttributeFilter(name, values));
        }
        return List.copyOf(attributes);
    }

    private CatalogDiscoveryCondition condition(String value) {
        return switch (normalized(value)) {
            case "new" -> CatalogDiscoveryCondition.NEW;
            case "secondhand" -> CatalogDiscoveryCondition.SECONDHAND;
            default -> throw AgentProductReadToolException.invalid(
                    "Condition must be new or secondhand.");
        };
    }

    private CatalogDiscoveryAttributeName attributeName(String value) {
        return switch (normalized(value)) {
            case "color" -> CatalogDiscoveryAttributeName.COLOR;
            case "size" -> CatalogDiscoveryAttributeName.SIZE;
            case "target gender" -> CatalogDiscoveryAttributeName.TARGET_GENDER;
            default -> throw AgentProductReadToolException.invalid(
                    "Attribute name must be Color, Size, or Target gender.");
        };
    }

    private CatalogDiscoveryPriceTier priceTier(String value) {
        return switch (normalized(value)) {
            case "low" -> CatalogDiscoveryPriceTier.LOW;
            case "medium" -> CatalogDiscoveryPriceTier.MEDIUM;
            case "high" -> CatalogDiscoveryPriceTier.HIGH;
            default -> throw AgentProductReadToolException.invalid(
                    "Price tier must be low, medium, or high.");
        };
    }

    private CatalogDiscoveryLocation location(
            String country,
            String region,
            String postalCode,
            String field
    ) {
        if (country == null || !country.trim().matches("(?i)[A-Z]{2}")) {
            throw AgentProductReadToolException.invalid(
                    field + " country must be an ISO 3166-1 alpha-2 code.");
        }
        return new CatalogDiscoveryLocation(country, region, postalCode);
    }

    private CatalogDiscoveryPrice price(SearchCatalogAgentToolInput.Price input) {
        if (input == null) {
            return null;
        }
        Long min = minorUnits(input.minUsd(), "price.minUsd");
        Long max = minorUnits(input.maxUsd(), "price.maxUsd");
        if (min == null && max == null) {
            throw AgentProductReadToolException.invalid("Price needs a minimum or maximum USD amount.");
        }
        if (min != null && max != null && min > max) {
            throw AgentProductReadToolException.invalid("Minimum price must not exceed maximum price.");
        }
        return new CatalogDiscoveryPrice(min, max);
    }

    private CatalogDiscoveryRating rating(SearchCatalogAgentToolInput.Rating input) {
        if (input == null) {
            return null;
        }
        if (input.variantMinimum() == null && input.variantMinimumCount() == null) {
            throw AgentProductReadToolException.invalid(
                    "Rating needs a minimum value or minimum review count.");
        }
        try {
            return new CatalogDiscoveryRating(input.variantMinimum(), input.variantMinimumCount());
        } catch (IllegalArgumentException exception) {
            throw AgentProductReadToolException.invalid(exception.getMessage());
        }
    }

    private Long minorUnits(BigDecimal amount, String field) {
        if (amount == null) {
            return null;
        }
        if (amount.signum() < 0 || amount.compareTo(BigDecimal.valueOf(1_000_000)) > 0) {
            throw AgentProductReadToolException.invalid(field + " must be between 0 and 1000000 USD.");
        }
        try {
            return amount.movePointRight(2)
                    .setScale(0, RoundingMode.UNNECESSARY)
                    .longValueExact();
        } catch (ArithmeticException exception) {
            throw AgentProductReadToolException.invalid(field + " must have at most two decimal places.");
        }
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String requiredQuery(String query) {
        if (query == null || query.isBlank()) {
            throw AgentProductReadToolException.invalid("A catalog search query is required.");
        }
        String value = query.trim();
        if (value.length() > 500) {
            throw AgentProductReadToolException.invalid("The query must be at most 500 characters.");
        }
        return value;
    }
}
