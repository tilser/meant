package com.meant.api.module.agent.service.tool;

import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.service.AgentContextProfileService;
import com.meant.api.module.agent.service.AgentInventoryProductAnchorService;
import com.meant.api.module.agent.service.AgentJsonSupport;
import com.meant.api.module.agent.service.AgentProductReadReferenceService;
import com.meant.api.module.agent.service.AgentProductReadResultService;
import com.meant.api.module.agent.service.AgentProductSearchQualificationService;
import com.meant.api.module.agent.service.AgentSimilaritySearchQualificationService;
import com.meant.api.module.agent.service.command.BindAgentSimilaritySearchQualificationCommand;
import com.meant.api.module.agent.service.command.QualifyAgentProductSearchCommand;
import com.meant.api.module.agent.service.dto.AgentArtifact;
import com.meant.api.module.agent.service.dto.AgentInventoryProductAnchor;
import com.meant.api.module.agent.service.dto.AgentProductListResult;
import com.meant.api.module.agent.service.dto.AgentProductReferenceResult;
import com.meant.api.module.agent.service.dto.AgentSimilarityAnchorResult;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentToolExecutionResult;
import com.meant.api.module.agent.service.dto.FindSimilarProductsAgentToolInput;
import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.user.service.UserSimilarProductSearchService;
import com.meant.api.module.user.service.command.SearchSimilarUserProductsCommand;
import com.meant.api.module.user.service.dto.UserGroupedProductSearchResult;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FindSimilarProductsAgentTool implements AgentTool {

    private static final AgentToolDescriptor DESCRIPTOR = new AgentToolDescriptor(
            "find_similar_products",
            "Find products similar to a product or owned inventory item previously shown in this conversation. "
                    + "For an owned item, call only after search_inventory resolves one complete, unambiguous match, "
                    + "or after the user chooses a match and get_inventory_item loads it. Pass that inventoryItemId. "
                    + "Inventory anchors preserve selected size and options. Results include appliedFilters and "
                    + "unsetFilters so you can decide whether a follow-up question would improve relevance.",
            """
            {"type":"object","properties":{"canonicalProductKey":{"type":"string","minLength":1,"maxLength":200},"inventoryItemId":{"type":"string","format":"uuid"},"query":{"type":"string","maxLength":500}},"anyOf":[{"required":["canonicalProductKey"]},{"required":["inventoryItemId"]}],"additionalProperties":false}
            """,
            "1",
            AgentToolRisk.READ
    );

    private final AgentJsonSupport json;
    private final AgentContextProfileService profileService;
    private final AgentProductReadReferenceService referenceService;
    private final AgentInventoryProductAnchorService inventoryAnchorService;
    private final AgentProductReadResultService resultService;
    private final AgentProductSearchQualificationService qualificationService;
    private final AgentSimilaritySearchQualificationService similarityQualificationService;
    private final UserSimilarProductSearchService similarProductSearchService;

    @Override
    public AgentToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AgentToolExecutionResult execute(AgentToolExecutionContext context, String argumentsJson) {
        FindSimilarProductsAgentToolInput input = json.readArguments(argumentsJson, FindSimilarProductsAgentToolInput.class);
        String query = input.query() == null || input.query().isBlank()
                ? "similar products"
                : input.query().trim();
        if (query.length() > 500) {
            throw AgentProductReadToolException.invalid("The query must be at most 500 characters.");
        }
        AgentSimilarityAnchorResult similarityAnchor = anchor(context, input, query);
        var profile = profileService.profile(context.userId());
        String authoritativeUserText = context.triggeringUserText() == null
                        || context.triggeringUserText().isBlank()
                ? query
                : context.triggeringUserText().trim();
        var qualificationCommand = new QualifyAgentProductSearchCommand(
                profile,
                context.conversationId(),
                context.merchantId(),
                context.qualificationContextMessageId(),
                context.qualificationRequestId(),
                authoritativeUserText,
                bounded(similarityAnchor.label(), 500)
        );
        UUID expectedQualificationId = qualificationCommand.requestQualificationId();
        if (expectedQualificationId == null) {
            throw AgentProductReadToolException.invalid(
                    "Similarity search requires a trusted buyer request.");
        }
        similarityQualificationService.bind(new BindAgentSimilaritySearchQualificationCommand(
                expectedQualificationId,
                context.userId(),
                context.conversationId(),
                context.merchantId(),
                similarityAnchor.canonicalProductKey(),
                similarityAnchor.inventoryItemId(),
                similarityAnchor.label(),
                authoritativeUserText
        ));
        var qualification = qualificationService.qualify(qualificationCommand);
        UUID qualificationId = qualification.qualificationId();
        if (!expectedQualificationId.equals(qualificationId)) {
            throw AgentProductReadToolException.invalid(
                    "Similarity search could not preserve its qualification identity.");
        }
        AgentSimilarityAnchorResult qualifiedAnchor = new AgentSimilarityAnchorResult(
                similarityAnchor.canonicalProductKey(),
                similarityAnchor.inventoryItemId(),
                similarityAnchor.label(),
                qualification.authoritativeQuery()
        );
        UserGroupedProductSearchResult result = similarProductSearchService.search(
                profile,
                new SearchSimilarUserProductsCommand(
                        context.userId(),
                        qualifiedAnchor.canonicalProductKey(),
                        qualification.authoritativeQuery(),
                        qualificationId,
                        context.merchantId(),
                        context.buyerIp(),
                        context.userAgent(),
                        context.language()
                ),
                qualification.filters(),
                qualification.explicitAnyTargets(),
                qualification.profileSuppressionTargets()
        );
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
                        qualifiedAnchor
                ))
                .flatMap(List::stream)
                .toList();
        AgentProductListResult output = new AgentProductListResult(
                references,
                null,
                false,
                result.upstreamTruncated(),
                List.of(),
                qualifiedAnchor,
                List.of(),
                qualification.appliedFilters(),
                qualification.unsetFilters(),
                products.size()
        );
        return AgentToolExecutionResult.read(
                json.write(output), "Found " + products.size() + " similar product(s).", artifacts);
    }

    private AgentSimilarityAnchorResult anchor(
            AgentToolExecutionContext context,
            FindSimilarProductsAgentToolInput input,
            String query
    ) {
        boolean productSupplied = input.canonicalProductKey() != null && !input.canonicalProductKey().isBlank();
        boolean inventorySupplied = input.inventoryItemId() != null;
        if (productSupplied == inventorySupplied) {
            throw AgentProductReadToolException.invalid(
                    "Supply exactly one product reference or inventory item reference.");
        }
        if (productSupplied) {
            String canonicalProductKey = input.canonicalProductKey().trim();
            var reference = referenceService.requireProduct(context, canonicalProductKey);
            return new AgentSimilarityAnchorResult(
                    canonicalProductKey,
                    null,
                    firstText(reference.getLabel(), canonicalProductKey),
                    query
            );
        }
        var reference = referenceService.requireSoleInventoryItem(context, input.inventoryItemId());
        AgentInventoryProductAnchor anchor = inventoryAnchorService.anchor(
                context.userId(), input.inventoryItemId());
        return new AgentSimilarityAnchorResult(
                anchor.canonicalProductKey(),
                input.inventoryItemId(),
                firstText(reference.getLabel(), anchor.label(), anchor.canonicalProductKey()),
                query
        );
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String bounded(String value, int maximumLength) {
        return value == null || value.length() <= maximumLength
                ? value
                : value.substring(0, maximumLength);
    }

}
