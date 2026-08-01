package com.meant.api.module.agent.service.tool;

import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.service.AgentContextProfileService;
import com.meant.api.module.agent.service.AgentJsonSupport;
import com.meant.api.module.agent.service.AgentProductReadReferenceService;
import com.meant.api.module.agent.service.AgentProductReadResultService;
import com.meant.api.module.agent.service.dto.AgentProductListResult;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentToolExecutionResult;
import com.meant.api.module.agent.service.dto.GetProductAgentToolInput;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.RehydratedProductDetails;
import com.meant.api.module.user.exception.SelectedOfferResolutionException;
import com.meant.api.module.user.service.UserCanonicalProductDetailService;
import com.meant.api.module.user.service.UserProductVariantSelectionService;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.SelectUserProductVariantCommand;
import com.meant.api.module.user.service.dto.UserProductDetailResult;
import com.meant.api.module.user.service.dto.UserProductVariantSelectionResult;
import com.meant.api.module.user.service.query.GetUserCanonicalProductDetailQuery;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetProductAgentTool implements AgentTool {

    private static final AgentToolDescriptor DESCRIPTOR = new AgentToolDescriptor(
            "get_product",
            "Rehydrate current product facts, merchant-selectable options, variant availability, and offers for a "
                    + "product previously shown in this conversation. Canonical offers are not an exhaustive list "
                    + "of merchant variants. To obtain a cartable offer for a requested option combination, call "
                    + "select_product_variant with all required option values.",
            """
            {"type":"object","properties":{"canonicalProductKey":{"type":"string","minLength":1,"maxLength":200},"selectedOfferKey":{"type":"string","minLength":1,"maxLength":200}},"required":["canonicalProductKey"],"additionalProperties":false}
            """,
            "1",
            AgentToolRisk.READ
    );

    private final AgentJsonSupport json;
    private final AgentContextProfileService profileService;
    private final AgentProductReadReferenceService referenceService;
    private final AgentProductReadResultService resultService;
    private final UserCanonicalProductDetailService detailService;
    private final UserProductVariantSelectionService variantSelectionService;

    @Override
    public AgentToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AgentToolExecutionResult execute(AgentToolExecutionContext context, String argumentsJson) {
        GetProductAgentToolInput input = json.readArguments(argumentsJson, GetProductAgentToolInput.class);
        referenceService.requireProduct(context, input.canonicalProductKey());
        EnsureUserProfileCommand profile = profileService.profile(context.userId());
        UserProductDetailResult detail = detailService.get(
                profile,
                new GetUserCanonicalProductDetailQuery(
                        context.userId(), input.canonicalProductKey(), input.selectedOfferKey()));
        Offer selectedOffer = detail.product().offers().stream()
                .filter(offer -> offer.key().equals(detail.selectedOfferKey()))
                .findFirst()
                .orElseThrow(AgentException::notFound);
        RehydratedProductDetails variantDetails = variantDetails(profile, context, selectedOffer);
        AgentProductListResult output = new AgentProductListResult(
                List.of(resultService.reference(detail.product(), 1, variantDetails)),
                null,
                false,
                false,
                List.of(),
                null
        );
        return AgentToolExecutionResult.read(
                json.write(output),
                variantDetails == null
                        ? "Loaded current product detail and " + detail.product().offers().size()
                                + " offer(s); selectable variant details were unavailable."
                        : "Loaded current product detail, selectable variants, and "
                                + detail.product().offers().size() + " offer(s).",
                resultService.detailArtifacts(detail, 1)
        );
    }

    private RehydratedProductDetails variantDetails(
            EnsureUserProfileCommand profile,
            AgentToolExecutionContext context,
            Offer selectedOffer
    ) {
        try {
            UserProductVariantSelectionResult selection = variantSelectionService.select(
                    profile,
                    new SelectUserProductVariantCommand(
                            context.userId(),
                            selectedOffer.key(),
                            selectedOffer.selectedOptions().stream()
                                    .map(option -> new SelectUserProductVariantCommand.SelectedOption(
                                            option.name(),
                                            option.value()
                                    ))
                                    .toList(),
                            null
                    )
            );
            return selection.details();
        } catch (SelectedOfferResolutionException exception) {
            return null;
        }
    }
}
