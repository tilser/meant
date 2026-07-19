package com.meant.api.module.agent.service;

import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.service.dto.AgentArtifact;
import com.meant.api.module.agent.service.dto.AgentInventoryProductAnchor;
import com.meant.api.module.agent.service.dto.AgentProductListResult;
import com.meant.api.module.agent.service.dto.AgentProductReferenceResult;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentToolExecutionResult;
import com.meant.api.module.agent.service.dto.FindSimilarProductsAgentToolInput;
import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.user.service.UserSimilarProductSearchService;
import com.meant.api.module.user.service.command.SearchSimilarUserProductsCommand;
import com.meant.api.module.user.service.dto.UserGroupedProductSearchResult;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FindSimilarProductsAgentTool implements AgentTool {

    private static final AgentToolDescriptor DESCRIPTOR = new AgentToolDescriptor(
            "find_similar_products",
            "Find products similar to a product or owned inventory item previously shown in this conversation. Inventory anchors preserve selected size and options.",
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
    private final UserSimilarProductSearchService similarProductSearchService;

    @Override
    public AgentToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AgentToolExecutionResult execute(AgentToolExecutionContext context, String argumentsJson) {
        FindSimilarProductsAgentToolInput input = json.readArguments(argumentsJson, FindSimilarProductsAgentToolInput.class);
        String canonicalProductKey = anchor(context, input);
        String query = input.query() == null || input.query().isBlank()
                ? "similar products"
                : input.query().trim();
        if (query.length() > 500) {
            throw AgentProductReadToolException.invalid("The query must be at most 500 characters.");
        }
        UserGroupedProductSearchResult result = similarProductSearchService.search(
                profileService.profile(context.userId()),
                new SearchSimilarUserProductsCommand(
                        context.userId(), canonicalProductKey, query, null, null));
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
                references, null, false, result.upstreamTruncated(), List.of());
        return AgentToolExecutionResult.read(
                json.write(output), "Found " + products.size() + " similar product(s).", artifacts);
    }

    private String anchor(AgentToolExecutionContext context, FindSimilarProductsAgentToolInput input) {
        boolean productSupplied = input.canonicalProductKey() != null && !input.canonicalProductKey().isBlank();
        boolean inventorySupplied = input.inventoryItemId() != null;
        if (productSupplied == inventorySupplied) {
            throw AgentProductReadToolException.invalid(
                    "Supply exactly one product reference or inventory item reference.");
        }
        if (productSupplied) {
            referenceService.requireProduct(context, input.canonicalProductKey());
            return input.canonicalProductKey().trim();
        }
        referenceService.requireInventoryItem(context, input.inventoryItemId());
        AgentInventoryProductAnchor anchor = inventoryAnchorService.anchor(
                context.userId(), input.inventoryItemId());
        return anchor.canonicalProductKey();
    }
}
