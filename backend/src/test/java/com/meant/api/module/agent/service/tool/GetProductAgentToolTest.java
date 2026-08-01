package com.meant.api.module.agent.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.service.AgentContextProfileService;
import com.meant.api.module.agent.service.AgentJsonSupport;
import com.meant.api.module.agent.service.AgentProductReadReferenceService;
import com.meant.api.module.agent.service.AgentProductReadResultService;
import com.meant.api.module.agent.service.dto.AgentOfferReferenceResult;
import com.meant.api.module.agent.service.dto.AgentProductReferenceResult;
import com.meant.api.module.agent.service.dto.AgentProductVariantDetailsResult;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.GetProductAgentToolInput;
import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.OfferAvailabilityStatus;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import com.meant.api.module.catalog.service.dto.RehydratedProductDetails;
import com.meant.api.module.user.exception.SelectedOfferResolutionException;
import com.meant.api.module.user.service.UserCanonicalProductDetailService;
import com.meant.api.module.user.service.UserProductVariantSelectionService;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.SelectUserProductVariantCommand;
import com.meant.api.module.user.service.dto.UserProductDetailResult;
import com.meant.api.module.user.service.dto.UserProductVariantSelectionResult;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class GetProductAgentToolTest {

    @Test
    void exposesSiblingMerchantVariantsThatAreNotCanonicalOffers() throws Exception {
        UUID userId = UUID.randomUUID();
        AgentToolExecutionContext context = new AgentToolExecutionContext(
                userId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "About LETHAL SPEED RS MENS FOOTBALL: is there also black variant?"
        );
        EnsureUserProfileCommand profile = new EnsureUserProfileCommand(
                userId,
                "shopper@example.test",
                "Shopper",
                null
        );
        ProductAttribute coral = new ProductAttribute(
                "variant-option",
                "Colour",
                "Wht/Vivid Coral"
        );
        CanonicalProduct product = mock(CanonicalProduct.class);
        Offer offer = mock(Offer.class);
        UserProductDetailResult canonicalDetail = mock(UserProductDetailResult.class);
        RehydratedProductDetails merchantDetail = mock(RehydratedProductDetails.class);
        UserProductVariantSelectionResult variantSelection =
                new UserProductVariantSelectionResult(merchantDetail, null, false);

        AgentJsonSupport json = mock(AgentJsonSupport.class);
        AgentContextProfileService profiles = mock(AgentContextProfileService.class);
        AgentProductReadReferenceService references = mock(AgentProductReadReferenceService.class);
        AgentProductReadResultService results = mock(AgentProductReadResultService.class);
        UserCanonicalProductDetailService details = mock(UserCanonicalProductDetailService.class);
        UserProductVariantSelectionService variants = mock(UserProductVariantSelectionService.class);

        when(json.readArguments("{}", GetProductAgentToolInput.class))
                .thenReturn(new GetProductAgentToolInput("lethal-speed", null));
        when(json.write(any())).thenAnswer(invocation ->
                new ObjectMapper().writeValueAsString(invocation.getArgument(0)));
        when(profiles.profile(userId)).thenReturn(profile);
        when(details.get(any(), any())).thenReturn(canonicalDetail);
        when(canonicalDetail.product()).thenReturn(product);
        when(canonicalDetail.selectedOfferKey()).thenReturn("offer-coral");
        when(product.offers()).thenReturn(List.of(offer));
        when(offer.key()).thenReturn("offer-coral");
        when(offer.selectedOptions()).thenReturn(List.of(coral));
        when(variants.select(any(), any())).thenReturn(variantSelection);

        AgentProductReferenceResult reference = new AgentProductReferenceResult(
                1,
                "lethal-speed",
                "LETHAL SPEED RS MENS FOOTBALL",
                null,
                null,
                "offer-coral",
                List.of(new AgentOfferReferenceResult(
                        "offer-coral",
                        "Manning Shoes",
                        "Wht/Vivid Coral",
                        9_200L,
                        "USD",
                        OfferAvailabilityStatus.IN_STOCK,
                        List.of(coral)
                )),
                new AgentProductVariantDetailsResult(
                        List.of(new AgentProductVariantDetailsResult.OptionGroup(
                                "Colour",
                                List.of(
                                        new AgentProductVariantDetailsResult.OptionValue(
                                                "Wht/Vivid Coral",
                                                true,
                                                true
                                        ),
                                        new AgentProductVariantDetailsResult.OptionValue(
                                                "Black/Lemon",
                                                true,
                                                true
                                        )
                                )
                        )),
                        List.of(new AgentProductVariantDetailsResult.SelectedOption(
                                "Colour",
                                "Wht/Vivid Coral"
                        )),
                        "Wht/Vivid Coral",
                        true,
                        2,
                        2
                )
        );
        when(results.reference(product, 1, merchantDetail, null)).thenReturn(reference);
        when(results.detailArtifacts(canonicalDetail, 1)).thenReturn(List.of());

        GetProductAgentTool tool = new GetProductAgentTool(
                json,
                profiles,
                references,
                results,
                details,
                variants
        );

        var result = tool.execute(context, "{}");

        assertThat(result.resultJson())
                .contains("Wht/Vivid Coral")
                .contains("Black/Lemon")
                .contains("\"variantDetails\"");
        assertThat(result.safeSummary()).contains("selectable variants");
        assertThat(tool.descriptor().description())
                .contains("merchant-selectable options")
                .contains("not an exhaustive list");

        ArgumentCaptor<SelectUserProductVariantCommand> command =
                ArgumentCaptor.forClass(SelectUserProductVariantCommand.class);
        verify(variants).select(org.mockito.ArgumentMatchers.eq(profile), command.capture());
        assertThat(command.getValue().anchorOfferKey()).isEqualTo("offer-coral");
        assertThat(command.getValue().selectedOptions())
                .containsExactly(new SelectUserProductVariantCommand.SelectedOption(
                        "Colour",
                        "Wht/Vivid Coral"
                ));
    }

    @Test
    void keepsCanonicalProductFactsWhenSelectableVariantDetailsAreUnavailable() throws Exception {
        UUID userId = UUID.randomUUID();
        AgentToolExecutionContext context = new AgentToolExecutionContext(
                userId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "About LETHAL SPEED RS MENS FOOTBALL: is there also black variant?"
        );
        EnsureUserProfileCommand profile = new EnsureUserProfileCommand(
                userId,
                "shopper@example.test",
                "Shopper",
                null
        );
        CanonicalProduct product = mock(CanonicalProduct.class);
        Offer offer = mock(Offer.class);
        UserProductDetailResult canonicalDetail = mock(UserProductDetailResult.class);
        AgentJsonSupport json = mock(AgentJsonSupport.class);
        AgentContextProfileService profiles = mock(AgentContextProfileService.class);
        AgentProductReadReferenceService references = mock(AgentProductReadReferenceService.class);
        AgentProductReadResultService results = mock(AgentProductReadResultService.class);
        UserCanonicalProductDetailService details = mock(UserCanonicalProductDetailService.class);
        UserProductVariantSelectionService variants = mock(UserProductVariantSelectionService.class);

        when(json.readArguments("{}", GetProductAgentToolInput.class))
                .thenReturn(new GetProductAgentToolInput("lethal-speed", null));
        when(json.write(any())).thenAnswer(invocation ->
                new ObjectMapper().writeValueAsString(invocation.getArgument(0)));
        when(profiles.profile(userId)).thenReturn(profile);
        when(details.get(any(), any())).thenReturn(canonicalDetail);
        when(canonicalDetail.product()).thenReturn(product);
        when(canonicalDetail.selectedOfferKey()).thenReturn("offer-coral");
        when(product.offers()).thenReturn(List.of(offer));
        when(offer.key()).thenReturn("offer-coral");
        when(offer.selectedOptions()).thenReturn(List.of());
        when(variants.select(any(), any())).thenThrow(SelectedOfferResolutionException.rejected(
                SelectedOfferResolutionException.Failure.PROVIDER_FAILURE,
                "Current merchant product details are unavailable"
        ));
        AgentProductReferenceResult reference = new AgentProductReferenceResult(
                1,
                "lethal-speed",
                "LETHAL SPEED RS MENS FOOTBALL",
                null,
                null,
                "offer-coral",
                List.of()
        );
        when(results.reference(product, 1, null, null)).thenReturn(reference);
        when(results.detailArtifacts(canonicalDetail, 1)).thenReturn(List.of());

        GetProductAgentTool tool = new GetProductAgentTool(
                json,
                profiles,
                references,
                results,
                details,
                variants
        );

        var result = tool.execute(context, "{}");

        assertThat(result.resultJson())
                .contains("LETHAL SPEED RS MENS FOOTBALL")
                .doesNotContain("variantDetails");
        assertThat(result.safeSummary()).contains("selectable variant details were unavailable");
        verify(results).reference(product, 1, null, null);
    }
}
