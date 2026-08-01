package com.meant.api.module.agent.service.tool;

import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.service.AgentContextProfileService;
import com.meant.api.module.agent.service.AgentJsonSupport;
import com.meant.api.module.agent.service.AgentProductReadReferenceService;
import com.meant.api.module.agent.service.AgentProductReadResultService;
import com.meant.api.module.agent.service.dto.AgentProductListResult;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentToolExecutionResult;
import com.meant.api.module.agent.service.dto.PickRecommendedProductAgentToolInput;
import com.meant.api.module.catalog.service.dto.OfferAvailabilityStatus;
import com.meant.api.module.user.service.UserCanonicalProductDetailService;
import com.meant.api.module.user.service.dto.UserCanonicalProductsRehydrationResult;
import com.meant.api.module.user.service.dto.UserProductDetailResult;
import com.meant.api.module.user.service.query.RehydrateUserCanonicalProductsQuery;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PickRecommendedProductAgentTool implements AgentTool {

    private static final AgentToolDescriptor DESCRIPTOR = new AgentToolDescriptor(
            "pick_recommended_product",
            "Pick the highest-ranked currently purchasable product from an ordered list of previously shown candidates.",
            """
            {"type":"object","properties":{"canonicalProductKeys":{"type":"array","minItems":1,"maxItems":10,"uniqueItems":true,"items":{"type":"string","minLength":1,"maxLength":200}}},"required":["canonicalProductKeys"],"additionalProperties":false}
            """,
            "1",
            AgentToolRisk.READ
    );

    private final AgentJsonSupport json;
    private final AgentContextProfileService profileService;
    private final AgentProductReadReferenceService referenceService;
    private final AgentProductReadResultService resultService;
    private final UserCanonicalProductDetailService detailService;

    @Override
    public AgentToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AgentToolExecutionResult execute(AgentToolExecutionContext context, String argumentsJson) {
        PickRecommendedProductAgentToolInput input = json.readArguments(
                argumentsJson, PickRecommendedProductAgentToolInput.class);
        List<String> keys = normalizedKeys(input.canonicalProductKeys());
        keys.forEach(key -> referenceService.requireProduct(context, key));
        UserCanonicalProductsRehydrationResult rehydrated = detailService.rehydrate(
                profileService.profile(context.userId()),
                new RehydrateUserCanonicalProductsQuery(context.userId(), keys));
        UserProductDetailResult picked = rehydrated.products().stream()
                .filter(this::purchasable)
                .findFirst()
                .orElseThrow(() -> AgentProductReadToolException.invalid(
                        "None of the selected candidates is currently purchasable."));
        AgentProductListResult output = new AgentProductListResult(
                List.of(resultService.reference(
                        picked.product(),
                        1,
                        null,
                        picked.personalization()
                )),
                null,
                false,
                false,
                rehydrated.unavailableCanonicalProductKeys(),
                null
        );
        return AgentToolExecutionResult.read(
                json.write(output),
                "Picked the highest-ranked currently available candidate.",
                resultService.detailArtifacts(picked, 1)
        );
    }

    private boolean purchasable(UserProductDetailResult product) {
        return product.product().offers().stream().anyMatch(offer ->
                offer.availability().status() == OfferAvailabilityStatus.IN_STOCK
                        || offer.availability().status() == OfferAvailabilityStatus.PREORDER
                        || offer.availability().status() == OfferAvailabilityStatus.BACKORDER);
    }

    private List<String> normalizedKeys(List<String> values) {
        if (values == null || values.isEmpty() || values.size() > 10) {
            throw AgentProductReadToolException.invalid("Choose one to ten products for recommendation.");
        }
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank() || value.trim().length() > 200) {
                throw AgentProductReadToolException.invalid("Each product reference must be between 1 and 200 characters.");
            }
            keys.add(value.trim());
        }
        if (keys.size() != values.size()) {
            throw AgentProductReadToolException.invalid("Recommendation product references must not contain duplicates.");
        }
        return List.copyOf(keys);
    }
}
