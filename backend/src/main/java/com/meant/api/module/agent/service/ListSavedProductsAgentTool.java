package com.meant.api.module.agent.service;

import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.service.dto.AgentArtifact;
import com.meant.api.module.agent.service.dto.AgentSavedProductListResult;
import com.meant.api.module.agent.service.dto.AgentSavedProductReferenceResult;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentToolExecutionResult;
import com.meant.api.module.agent.service.dto.ListSavedProductsAgentToolInput;
import com.meant.api.module.user.service.UserSavedProductService;
import com.meant.api.module.user.service.dto.UserSavedProductResult;
import com.meant.api.module.user.service.query.ListSavedProductsQuery;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ListSavedProductsAgentTool implements AgentTool {

    private static final AgentToolDescriptor DESCRIPTOR = new AgentToolDescriptor(
            "list_saved_products",
            "List products the current user has saved, with freshly rehydrated offers when available.",
            """
            {"type":"object","properties":{"limit":{"type":"integer","minimum":1,"maximum":20}},"additionalProperties":false}
            """,
            "1",
            AgentToolRisk.READ
    );

    private final AgentJsonSupport json;
    private final AgentContextProfileService profileService;
    private final UserSavedProductService userSavedProductService;

    @Override
    public AgentToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AgentToolExecutionResult execute(AgentToolExecutionContext context, String argumentsJson) {
        ListSavedProductsAgentToolInput input = json.readArguments(argumentsJson, ListSavedProductsAgentToolInput.class);
        int limit = input.limit() == null ? 10 : input.limit();
        if (limit < 1 || limit > 20) {
            throw AgentProductReadToolException.invalid("Limit must be between 1 and 20.");
        }
        List<UserSavedProductResult> products = userSavedProductService.list(
                profileService.profile(context.userId()),
                new ListSavedProductsQuery(context.userId(), 0, limit));
        List<AgentSavedProductReferenceResult> references = IntStream.range(0, products.size())
                .mapToObj(index -> reference(products.get(index), index + 1))
                .toList();
        List<AgentArtifact> artifacts = IntStream.range(0, products.size())
                .mapToObj(index -> artifact(products.get(index), index + 1))
                .toList();
        return AgentToolExecutionResult.read(
                json.write(new AgentSavedProductListResult(references)),
                "Loaded " + products.size() + " saved product(s).",
                artifacts
        );
    }

    private AgentSavedProductReferenceResult reference(UserSavedProductResult product, int ordinal) {
        return new AgentSavedProductReferenceResult(
                ordinal, product.id(), product.name(), product.brand(), product.category(), product.imageUrl(),
                product.match(), product.priceFromMinorUnits(), product.priceCurrency(), product.offers());
    }

    private AgentArtifact artifact(UserSavedProductResult product, int ordinal) {
        String offerKey = product.offers().isEmpty() ? null : product.offers().getFirst().offerKey();
        return new AgentArtifact(
                AgentArtifactType.SAVED_PRODUCT, ordinal, product.id(), product.name(), product.id(), offerKey,
                null, null, null, null, json.writeArtifact(product));
    }
}
