package com.meant.api.module.agent.service;

import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.service.dto.AgentArtifact;
import com.meant.api.module.agent.service.dto.AgentProductReadSelection;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentToolExecutionResult;
import com.meant.api.module.agent.service.dto.FindDiscountCodesAgentToolInput;
import com.meant.api.module.discount.service.DiscountCodeSearchService;
import com.meant.api.module.discount.service.command.SearchDiscountCodesCommand;
import com.meant.api.module.discount.service.dto.DiscountCodeSearchResult;
import com.meant.api.module.user.service.UserCommerceContextService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FindDiscountCodesAgentTool implements AgentTool {

    private static final AgentToolDescriptor DESCRIPTOR = new AgentToolDescriptor(
            "find_discount_codes",
            "Search and validate discount codes for a previously shown exact product offer.",
            """
            {"type":"object","properties":{"canonicalProductKey":{"type":"string","minLength":1,"maxLength":200},"selectedOfferKey":{"type":"string","minLength":1,"maxLength":200},"quantity":{"type":"integer","minimum":1,"maximum":20}},"required":["canonicalProductKey"],"additionalProperties":false}
            """,
            "1",
            AgentToolRisk.READ
    );

    private final AgentJsonSupport json;
    private final AgentContextProfileService profileService;
    private final AgentProductReadSelectionService selectionService;
    private final UserCommerceContextService commerceContextService;
    private final DiscountCodeSearchService discountCodeSearchService;

    @Override
    public AgentToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AgentToolExecutionResult execute(AgentToolExecutionContext context, String argumentsJson) {
        FindDiscountCodesAgentToolInput input = json.readArguments(
                argumentsJson, FindDiscountCodesAgentToolInput.class);
        int quantity = input.quantity() == null ? 1 : input.quantity();
        if (quantity < 1 || quantity > 20) {
            throw AgentProductReadToolException.invalid("Quantity must be between 1 and 20.");
        }
        AgentProductReadSelection selection = selectionService.select(
                context, input.canonicalProductKey(), input.selectedOfferKey(), true);
        if (selection.offer().identity().externalVariantIdentity() == null) {
            throw AgentProductReadToolException.invalid("Discount search requires an exact variant offer.");
        }
        var profile = profileService.profile(context.userId());
        String country = commerceContextService.find(context.userId()).countryCode();
        DiscountCodeSearchResult result = discountCodeSearchService.search(new SearchDiscountCodesCommand(
                context.userId(),
                selection.merchantIntegration().merchantId(),
                selection.merchantIntegration().verifiedDomain(),
                List.of(new SearchDiscountCodesCommand.Item(
                        selection.offer().identity().externalVariantIdentity().value(), quantity)),
                new SearchDiscountCodesCommand.BuyerIdentity(
                        profile.email(), null, profile.firstName(), profile.surname(), country),
                List.of(),
                List.of(),
                List.of()
        ));
        AgentArtifact artifact = new AgentArtifact(
                AgentArtifactType.DISCOUNT_CODES,
                1,
                "discount-codes:" + selection.detail().product().key() + ":" + selection.offer().key(),
                "Discount codes for " + selection.detail().product().title(),
                selection.detail().product().key(),
                selection.offer().key(),
                null, null, null, null,
                json.writeArtifact(result)
        );
        return AgentToolExecutionResult.read(
                json.write(result),
                "Found " + result.codes().size() + " validated discount code(s).",
                List.of(artifact)
        );
    }
}
