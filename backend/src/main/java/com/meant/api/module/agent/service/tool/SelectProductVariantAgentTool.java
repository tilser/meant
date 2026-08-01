package com.meant.api.module.agent.service.tool;

import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.entity.AgentArtifactReference;
import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.service.AgentContextProfileService;
import com.meant.api.module.agent.service.AgentJsonSupport;
import com.meant.api.module.agent.service.AgentProductReadReferenceService;
import com.meant.api.module.agent.service.AgentProductReadResultService;
import com.meant.api.module.agent.service.dto.AgentArtifact;
import com.meant.api.module.agent.service.dto.AgentOfferReferenceResult;
import com.meant.api.module.agent.service.dto.AgentProductVariantDetailsResult;
import com.meant.api.module.agent.service.dto.AgentProductVariantSelectionResult;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentToolExecutionResult;
import com.meant.api.module.agent.service.dto.SelectProductVariantAgentToolInput;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.user.service.UserProductVariantSelectionService;
import com.meant.api.module.user.service.command.SelectUserProductVariantCommand;
import com.meant.api.module.user.service.dto.UserProductVariantSelectionResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SelectProductVariantAgentTool implements AgentTool {

    private static final AgentToolDescriptor DESCRIPTOR = new AgentToolDescriptor(
            "select_product_variant",
            "Resolve a buyer-requested complete option combination to one exact current server-issued offer before "
                    + "any cart mutation. Use this whenever the buyer specifies or changes a size, color, or other "
                    + "variant option. Include every option required for the combination, using get_product first "
                    + "when needed. Pass only the returned selectedOfferKey to prepare_carts or add_cart_line, and "
                    + "only when exactMatch and cartable are true. Never substitute the input anchor/default offer.",
            """
                    {"type":"object","additionalProperties":false,"required":["offerKey","selectedOptions"],"properties":{"offerKey":{"type":"string","minLength":1,"maxLength":200},"selectedOptions":{"type":"array","minItems":1,"maxItems":20,"items":{"type":"object","additionalProperties":false,"required":["name","value"],"properties":{"name":{"type":"string","minLength":1,"maxLength":200},"value":{"type":"string","minLength":1,"maxLength":500}}}},"preferredOptionName":{"type":"string","minLength":1,"maxLength":200}}}
                    """,
            "1",
            AgentToolRisk.READ
    );

    private final AgentJsonSupport json;
    private final AgentContextProfileService profileService;
    private final AgentProductReadReferenceService referenceService;
    private final AgentProductReadResultService resultService;
    private final UserProductVariantSelectionService variantSelectionService;

    @Override
    public AgentToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AgentToolExecutionResult execute(AgentToolExecutionContext context, String argumentsJson) {
        SelectProductVariantAgentToolInput input = json.readArguments(
                argumentsJson, SelectProductVariantAgentToolInput.class);
        AgentArtifactReference anchor = referenceService.requireOffer(context, input.offerKey());
        String canonicalProductKey = anchor.getCanonicalProductKey();
        if (canonicalProductKey == null || canonicalProductKey.isBlank()) {
            throw AgentException.notFound();
        }
        UserProductVariantSelectionResult selection = variantSelectionService.select(
                profileService.profile(context.userId()),
                new SelectUserProductVariantCommand(
                        context.userId(),
                        input.offerKey(),
                        input.selectedOptions().stream()
                                .map(option -> new SelectUserProductVariantCommand.SelectedOption(
                                        option.name(), option.value()))
                                .toList(),
                        input.preferredOptionName()
                )
        );
        Offer exactOffer = selection.selectedOffer();
        AgentOfferReferenceResult selectedOffer = exactOffer == null
                ? null
                : resultService.offerReference(exactOffer);
        AgentProductVariantSelectionResult result = new AgentProductVariantSelectionResult(
                canonicalProductKey,
                input.offerKey(),
                exactOffer != null,
                exactOffer == null ? null : exactOffer.key(),
                selectedOffer,
                selection.cartable(),
                AgentProductVariantDetailsResult.from(selection.details())
        );
        List<AgentArtifact> artifacts = exactOffer == null || !selection.cartable()
                ? List.of()
                : List.of(new AgentArtifact(
                        AgentArtifactType.OFFER,
                        1,
                        AgentProductReadReferenceService.variantSelectionStableKey(exactOffer.key()),
                        label(anchor.getLabel(), selectedOffer),
                        canonicalProductKey,
                        exactOffer.key(),
                        null,
                        null,
                        null,
                        null,
                        json.writeArtifact(selectedOffer)
                ));
        return AgentToolExecutionResult.read(
                json.write(result),
                summary(exactOffer != null, selection.cartable()),
                artifacts
        );
    }

    private String summary(boolean exactMatch, boolean cartable) {
        if (!exactMatch) {
            return "The requested options did not resolve to one exact offer; the cart was not changed.";
        }
        if (!cartable) {
            return "Resolved the exact requested variant, but it is not currently cartable; the cart was not changed.";
        }
        return "Resolved the exact requested variant to a cartable server-issued offer.";
    }

    private String label(String anchorLabel, AgentOfferReferenceResult offer) {
        String base = anchorLabel == null || anchorLabel.isBlank() ? "Selected product variant" : anchorLabel;
        return offer.variant() == null || offer.variant().isBlank()
                ? base
                : base + " — " + offer.variant();
    }
}
