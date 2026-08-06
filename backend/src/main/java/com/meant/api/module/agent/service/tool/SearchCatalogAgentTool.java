package com.meant.api.module.agent.service.tool;

import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.service.AgentContextProfileService;
import com.meant.api.module.agent.service.AgentJsonSupport;
import com.meant.api.module.agent.service.AgentProductReadResultService;
import com.meant.api.module.agent.service.AgentProductSearchQualificationService;
import com.meant.api.module.agent.service.AgentSimilaritySearchQualificationService;
import com.meant.api.module.agent.service.command.QualifyAgentProductSearchCommand;
import com.meant.api.module.agent.service.dto.AgentAppliedSearchFilter;
import com.meant.api.module.agent.service.dto.AgentArtifact;
import com.meant.api.module.agent.service.dto.AgentProductListResult;
import com.meant.api.module.agent.service.dto.AgentProductReferenceResult;
import com.meant.api.module.agent.service.dto.AgentSimilarityAnchorResult;
import com.meant.api.module.agent.service.dto.AgentSimilaritySearchQualificationContext;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentToolExecutionResult;
import com.meant.api.module.agent.service.dto.SearchCatalogAgentToolInput;
import com.meant.api.module.agent.service.query.GetAgentSimilaritySearchQualificationQuery;
import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryAttributeFilter;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryAttributeName;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryCondition;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryFilters;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryLocation;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryPrice;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryPriceTier;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryRating;
import com.meant.api.module.user.constant.UserCurrency;
import com.meant.api.module.user.constant.UserProductSearchPagination;
import com.meant.api.module.user.constant.UserProductSearchDecisionSource;
import com.meant.api.module.user.constant.UserProductSearchQuestionTarget;
import com.meant.api.module.user.service.UserGroupedProductSearchService;
import com.meant.api.module.user.service.UserProductSearchCatalogInputBuilder;
import com.meant.api.module.user.service.UserSettingsService;
import com.meant.api.module.user.service.UserSimilarProductSearchService;
import com.meant.api.module.user.service.command.SearchSimilarUserProductsCommand;
import com.meant.api.module.user.service.command.SearchUserProductsCommand;
import com.meant.api.module.user.service.dto.UserGroupedProductSearchResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SearchCatalogAgentTool implements AgentTool {

    private static final AgentToolDescriptor DESCRIPTOR = new AgentToolDescriptor(
            "search_catalog",
            "Search and rank the commerce catalog. The server enriches the request with conversation and profile "
                    + "filter advice, and the result reports appliedFilters and unsetFilters. Supply only filters that "
                    + "are relevant to this request; missing filters never prevent a search.",
            """
            {
              "type":"object",
                "properties":{
                "query":{"type":"string","minLength":1,"maxLength":500},
                "shipsTo":{"type":"object","additionalProperties":false,"required":["country"],"properties":{"country":{"type":"string","pattern":"^[A-Za-z]{2}$"},"region":{"type":"string","maxLength":120},"postalCode":{"type":"string","maxLength":30}}},
                "shipsFrom":{"type":"array","maxItems":10,"items":{"type":"object","additionalProperties":false,"required":["country"],"properties":{"country":{"type":"string","pattern":"^[A-Za-z]{2}$"}}}},
                "price":{"type":"object","description":"Major units in the currency selected in Account settings.","additionalProperties":false,"properties":{"minAmount":{"type":"number","minimum":0,"maximum":1000000,"multipleOf":0.01},"maxAmount":{"type":"number","minimum":0,"maximum":1000000,"multipleOf":0.01}}},
                "conditions":{"type":"array","maxItems":2,"items":{"type":"string","enum":["new","secondhand"]}},
                "attributes":{"type":"array","maxItems":3,"items":{"type":"object","additionalProperties":false,"required":["name","values"],"properties":{"name":{"type":"string","enum":["Color","Size","Target gender"]},"values":{"type":"array","minItems":1,"maxItems":20,"items":{"type":"string","minLength":1,"maxLength":100}}}}},
                "rating":{"type":"object","additionalProperties":false,"properties":{"variantMinimum":{"type":"number","minimum":0,"maximum":5},"variantMinimumCount":{"type":"integer","minimum":0}}},
                "priceTiers":{"type":"array","maxItems":3,"items":{"type":"string","enum":["low","medium","high"]}},
                "offset":{"type":"integer","minimum":0,"maximum":99},
                "limit":{"type":"integer","minimum":1,"maximum":20}
              },
              "required":["query"],
              "additionalProperties":false
            }
            """,
            "4",
            AgentToolRisk.READ
    );

    private final AgentJsonSupport json;
    private final AgentContextProfileService profileService;
    private final AgentProductReadResultService resultService;
    private final AgentProductSearchQualificationService qualificationService;
    private final AgentSimilaritySearchQualificationService similarityQualificationService;
    private final UserGroupedProductSearchService searchService;
    private final UserSimilarProductSearchService similarProductSearchService;
    private final UserSettingsService settingsService;
    private final UserProductSearchCatalogInputBuilder catalogInputBuilder;

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
        String preferredCurrency = UserCurrency.normalizeOrDefault(settingsService.get(profile).currency());
        String authoritativeUserText = context.triggeringUserText() == null
                || context.triggeringUserText().isBlank()
                ? query
                : context.triggeringUserText().trim();
        String searchCurrency = catalogInputBuilder.resolveSearchCurrency(
                authoritativeUserText,
                preferredCurrency
        );
        CatalogDiscoveryFilters requestedFilters = filters(input, searchCurrency);
        var qualificationCommand = new QualifyAgentProductSearchCommand(
                profile,
                context.conversationId(),
                context.merchantId(),
                context.qualificationContextMessageId(),
                context.qualificationRequestId(),
                authoritativeUserText,
                null
        );
        java.util.UUID similarityLookupId = qualificationCommand.requestQualificationId();
        var preboundSimilarityContext = similarityContext(context, similarityLookupId);
        qualificationCommand = new QualifyAgentProductSearchCommand(
                profile,
                context.conversationId(),
                context.merchantId(),
                context.qualificationContextMessageId(),
                context.qualificationRequestId(),
                authoritativeUserText,
                preboundSimilarityContext
                        .map(value -> bounded(value.anchorLabel(), 500))
                        .orElse(null)
        );
        var qualification = qualificationService.qualify(qualificationCommand);
        requireMatchingSimilarityQualification(preboundSimilarityContext, qualification.qualificationId());
        var resolvedSimilarityContext = preboundSimilarityContext.or(() ->
                similarityContext(context, qualification.qualificationId()));
        requireMatchingSimilarityQualification(resolvedSimilarityContext, qualification.qualificationId());
        CatalogDiscoveryFilters filters = mergeFilters(qualification.filters(), requestedFilters);
        Map<String, AgentAppliedSearchFilter> appliedFilters = mergeAppliedFilters(
                qualification.appliedFilters(),
                requestedFilters
        );
        List<UserProductSearchQuestionTarget> unsetFilters = resolvedUnsetFilters(
                qualification.unsetFilters(),
                requestedFilters
        );
        if (resolvedSimilarityContext.isPresent()) {
            var bound = resolvedSimilarityContext.get();
            AgentSimilarityAnchorResult similarityAnchor = new AgentSimilarityAnchorResult(
                    bound.canonicalProductKey(),
                    bound.inventoryItemId(),
                    bound.anchorLabel(),
                    qualification.authoritativeQuery()
            );
            UserGroupedProductSearchResult result = similarProductSearchService.search(
                    profile,
                    new SearchSimilarUserProductsCommand(
                            context.userId(),
                            bound.canonicalProductKey(),
                            qualification.authoritativeQuery(),
                            qualification.qualificationId(),
                            context.merchantId(),
                            context.buyerIp(),
                            context.userAgent(),
                            context.language()
                    ),
                    filters,
                    qualification.explicitAnyTargets(),
                    qualification.profileSuppressionTargets()
            );
            return similarityResponse(result, similarityAnchor, appliedFilters, unsetFilters);
        }

        SearchUserProductsCommand command = new SearchUserProductsCommand(
                context.userId(),
                qualification.authoritativeQuery(),
                context.merchantId(),
                context.buyerIp(),
                context.userAgent(),
                context.language(),
                offset,
                limit
        );
        UserGroupedProductSearchResult result = searchService.search(
                profile,
                command,
                filters,
                qualification.explicitAnyTargets(),
                qualification.profileSuppressionTargets()
        );
        return response(result, List.of(), appliedFilters, unsetFilters);
    }

    private void requireMatchingSimilarityQualification(
            Optional<AgentSimilaritySearchQualificationContext> similarityContext,
            java.util.UUID qualificationId
    ) {
        if (similarityContext.isPresent()
                && !similarityContext.get().qualificationId().equals(qualificationId)) {
            throw AgentProductReadToolException.invalid(
                    "The similarity anchor does not belong to this product-search qualification.");
        }
    }

    private Optional<AgentSimilaritySearchQualificationContext> similarityContext(
            AgentToolExecutionContext context,
            java.util.UUID qualificationId
    ) {
        if (qualificationId == null) {
            return Optional.empty();
        }
        return similarityQualificationService.find(new GetAgentSimilaritySearchQualificationQuery(
                qualificationId,
                context.userId(),
                context.conversationId(),
                context.merchantId()
        ));
    }

    private AgentToolExecutionResult similarityResponse(
            UserGroupedProductSearchResult result,
            AgentSimilarityAnchorResult similarityAnchor,
            Map<String, AgentAppliedSearchFilter> appliedFilters,
            List<UserProductSearchQuestionTarget> unsetFilters
    ) {
        List<CanonicalProduct> products = result.products();
        List<AgentProductReferenceResult> references = IntStream.range(0, products.size())
                .mapToObj(index -> resultService.reference(
                        products.get(index),
                        index + 1,
                        null,
                        result.productPersonalizations().get(products.get(index).key())
                ))
                .toList();
        List<AgentArtifact> artifacts = IntStream.range(0, products.size())
                .mapToObj(index -> resultService.discoveryArtifacts(
                        products.get(index),
                        index + 1,
                        result.productRankingExplanations().get(products.get(index).key()),
                        result.productPersonalizations().get(products.get(index).key()),
                        result.offerRankingExplanations(),
                        similarityAnchor
                ))
                .flatMap(List::stream)
                .toList();
        AgentProductListResult output = new AgentProductListResult(
                references,
                null,
                false,
                result.upstreamTruncated(),
                List.of(),
                similarityAnchor,
                List.of(),
                appliedFilters,
                unsetFilters,
                products.size()
        );
        return AgentToolExecutionResult.read(
                json.write(output),
                "Found " + products.size() + " similar product(s).",
                artifacts
        );
    }

    private AgentToolExecutionResult response(
            UserGroupedProductSearchResult result,
            List<String> searchAdjustments,
            Map<String, AgentAppliedSearchFilter> appliedFilters,
            List<UserProductSearchQuestionTarget> unsetFilters
    ) {
        List<CanonicalProduct> products = result.products();
        List<AgentProductReferenceResult> references = IntStream.range(0, products.size())
                .mapToObj(index -> resultService.reference(
                        products.get(index),
                        index + 1,
                        null,
                        result.productPersonalizations().get(products.get(index).key())
                ))
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
                searchAdjustments,
                appliedFilters,
                unsetFilters,
                products.size()
        );
        String summary = "Found " + products.size() + " grounded product option(s).";
        if (!searchAdjustments.isEmpty()) {
            summary += " " + searchAdjustments.getFirst();
        }
        return AgentToolExecutionResult.read(
                json.write(output), summary, artifacts);
    }

    private CatalogDiscoveryFilters filters(SearchCatalogAgentToolInput input, String preferredCurrency) {
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
        CatalogDiscoveryPrice price = price(input.price(), preferredCurrency);
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

    private String bounded(String value, int maximumLength) {
        return value == null || value.length() <= maximumLength
                ? value
                : value.substring(0, maximumLength);
    }

    private CatalogDiscoveryFilters mergeFilters(
            CatalogDiscoveryFilters advised,
            CatalogDiscoveryFilters requested
    ) {
        if (requested == null) {
            return advised;
        }
        CatalogDiscoveryFilters base = advised == null
                ? new CatalogDiscoveryFilters(
                        null, List.of(), null, List.of(), null, List.of(), List.of(), List.of(), null, List.of())
                : advised;
        return new CatalogDiscoveryFilters(
                base.available(),
                requested.conditions().isEmpty() ? base.conditions() : requested.conditions(),
                requested.shipsTo() == null ? base.shipsTo() : requested.shipsTo(),
                requested.shipsFrom().isEmpty() ? base.shipsFrom() : requested.shipsFrom(),
                requested.price() == null ? base.price() : requested.price(),
                base.shopIds(),
                base.categoryIds(),
                mergeAttributes(base.attributes(), requested.attributes()),
                requested.rating() == null ? base.rating() : requested.rating(),
                requested.priceTiers().isEmpty() ? base.priceTiers() : requested.priceTiers()
        );
    }

    private List<CatalogDiscoveryAttributeFilter> mergeAttributes(
            List<CatalogDiscoveryAttributeFilter> advised,
            List<CatalogDiscoveryAttributeFilter> requested
    ) {
        Map<CatalogDiscoveryAttributeName, CatalogDiscoveryAttributeFilter> merged = new LinkedHashMap<>();
        advised.forEach(attribute -> merged.put(attribute.name(), attribute));
        requested.forEach(attribute -> merged.put(attribute.name(), attribute));
        return List.copyOf(merged.values());
    }

    private Map<String, AgentAppliedSearchFilter> mergeAppliedFilters(
            Map<String, AgentAppliedSearchFilter> advised,
            CatalogDiscoveryFilters requested
    ) {
        Map<String, AgentAppliedSearchFilter> merged = new LinkedHashMap<>(advised);
        if (requested == null) {
            return Map.copyOf(merged);
        }
        putRequested(merged, "condition", requested.conditions().stream().map(Enum::name).toList());
        putRequested(merged, "shipsTo", locationValues(requested.shipsTo()));
        putRequested(merged, "shipsFrom", requested.shipsFrom().stream()
                .flatMap(location -> locationValues(location).stream())
                .toList());
        putRequested(merged, "price", rangeValues(
                requested.price() == null ? null : requested.price().min(),
                requested.price() == null ? null : requested.price().max()
        ));
        requested.attributes().forEach(attribute -> putRequested(
                merged,
                switch (attribute.name()) {
                    case COLOR -> "color";
                    case SIZE -> "size";
                    case TARGET_GENDER -> "targetGender";
                },
                attribute.values()
        ));
        putRequested(merged, "rating", rangeValues(
                requested.rating() == null ? null : requested.rating().variantMinimum(),
                requested.rating() == null ? null : requested.rating().variantMinimumCount()
        ));
        putRequested(merged, "priceTier", requested.priceTiers().stream().map(Enum::name).toList());
        return Map.copyOf(merged);
    }

    private void putRequested(
            Map<String, AgentAppliedSearchFilter> filters,
            String name,
            List<String> values
    ) {
        if (!values.isEmpty()) {
            filters.put(name, new AgentAppliedSearchFilter(
                    values,
                    UserProductSearchDecisionSource.CURRENT_USER_TURN
            ));
        }
    }

    private List<UserProductSearchQuestionTarget> resolvedUnsetFilters(
            List<UserProductSearchQuestionTarget> advised,
            CatalogDiscoveryFilters requested
    ) {
        if (requested == null) {
            return advised;
        }
        Set<UserProductSearchQuestionTarget> unset = new LinkedHashSet<>(advised);
        if (!requested.conditions().isEmpty()) {
            unset.remove(UserProductSearchQuestionTarget.CONDITION);
        }
        if (requested.shipsTo() != null) {
            unset.remove(UserProductSearchQuestionTarget.SHIPS_TO);
        }
        if (!requested.shipsFrom().isEmpty()) {
            unset.remove(UserProductSearchQuestionTarget.SHIPS_FROM);
        }
        if (requested.price() != null) {
            unset.remove(UserProductSearchQuestionTarget.PRICE);
        }
        requested.attributes().forEach(attribute -> unset.remove(switch (attribute.name()) {
            case COLOR -> UserProductSearchQuestionTarget.COLOR;
            case SIZE -> UserProductSearchQuestionTarget.SIZE;
            case TARGET_GENDER -> UserProductSearchQuestionTarget.TARGET_GENDER;
        }));
        if (requested.rating() != null) {
            unset.remove(UserProductSearchQuestionTarget.RATING);
        }
        if (!requested.priceTiers().isEmpty()) {
            unset.remove(UserProductSearchQuestionTarget.PRICE_TIER);
        }
        return List.copyOf(unset);
    }

    private List<String> locationValues(CatalogDiscoveryLocation location) {
        if (location == null) {
            return List.of();
        }
        return List.of(java.util.stream.Stream.of(
                        location.country(), location.region(), location.postalCode())
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.joining(" / ")));
    }

    private List<String> rangeValues(Number minimum, Number maximum) {
        List<String> values = new ArrayList<>();
        if (minimum != null) {
            values.add("min=" + minimum);
        }
        if (maximum != null) {
            values.add("max=" + maximum);
        }
        return List.copyOf(values);
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

    private CatalogDiscoveryPrice price(SearchCatalogAgentToolInput.Price input, String currency) {
        if (input == null) {
            return null;
        }
        Long min = minorUnits(input.minAmount(), "price.minAmount", currency);
        Long max = minorUnits(input.maxAmount(), "price.maxAmount", currency);
        if (min == null && max == null) {
            throw AgentProductReadToolException.invalid(
                    "Price needs a minimum or maximum amount in " + currency + ".");
        }
        if (min != null && max != null && min > max) {
            throw AgentProductReadToolException.invalid("Minimum price must not exceed maximum price.");
        }
        return new CatalogDiscoveryPrice(min, max, currency);
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

    private Long minorUnits(BigDecimal amount, String field, String currency) {
        if (amount == null) {
            return null;
        }
        if (amount.signum() < 0 || amount.compareTo(BigDecimal.valueOf(1_000_000)) > 0) {
            throw AgentProductReadToolException.invalid(
                    field + " must be between 0 and 1000000 " + currency + ".");
        }
        int fractionDigits = Currency.getInstance(currency).getDefaultFractionDigits();
        try {
            return amount.movePointRight(Math.max(fractionDigits, 0))
                    .setScale(0, RoundingMode.UNNECESSARY)
                    .longValueExact();
        } catch (ArithmeticException exception) {
            throw AgentProductReadToolException.invalid(
                    field + " must use the minor-unit precision for " + currency + ".");
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
