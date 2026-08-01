package com.meant.api.module.agent.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.entity.AgentArtifactReference;
import com.meant.api.module.agent.service.AgentContextProfileService;
import com.meant.api.module.agent.service.AgentJsonSupport;
import com.meant.api.module.agent.service.AgentProductReadReferenceService;
import com.meant.api.module.agent.service.AgentProductReadResultService;
import com.meant.api.module.agent.service.dto.AgentOfferReferenceResult;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.SelectProductVariantAgentToolInput;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.OfferAvailabilityStatus;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import com.meant.api.module.catalog.service.dto.RehydratedProductDetails;
import com.meant.api.module.user.service.UserProductVariantSelectionService;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.SelectUserProductVariantCommand;
import com.meant.api.module.user.service.dto.UserProductVariantSelectionResult;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class SelectProductVariantAgentToolTest {

    @Test
    void issuesAnExactCartableOfferForTheCompleteRequestedVariant() throws Exception {
        UUID userId = UUID.randomUUID();
        AgentToolExecutionContext context = new AgentToolExecutionContext(
                userId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Add the White/Black shoes in size 11."
        );
        SelectProductVariantAgentToolInput input = new SelectProductVariantAgentToolInput(
                "offer-size-6-5",
                List.of(
                        new SelectProductVariantAgentToolInput.SelectedOption("Color", "White/Black"),
                        new SelectProductVariantAgentToolInput.SelectedOption("Size", "11")
                ),
                "Size"
        );
        AgentArtifactReference anchor = mock(AgentArtifactReference.class);
        Offer exactOffer = mock(Offer.class);
        RehydratedProductDetails details = details("White/Black", "11", true);
        UserProductVariantSelectionResult selection =
                new UserProductVariantSelectionResult(details, exactOffer, true);
        AgentOfferReferenceResult exactReference = new AgentOfferReferenceResult(
                "offer-size-11",
                "https://bestbuysoccer.com",
                "White/Black / 11",
                9_999L,
                "USD",
                OfferAvailabilityStatus.IN_STOCK,
                List.of(
                        new ProductAttribute("variant-option", "Color", "White/Black"),
                        new ProductAttribute("variant-option", "Size", "11")
                )
        );
        EnsureUserProfileCommand profile = new EnsureUserProfileCommand(
                userId, "shopper@example.test", "Shopper", null);
        AgentJsonSupport json = mock(AgentJsonSupport.class);
        AgentContextProfileService profiles = mock(AgentContextProfileService.class);
        AgentProductReadReferenceService references = mock(AgentProductReadReferenceService.class);
        AgentProductReadResultService results = mock(AgentProductReadResultService.class);
        UserProductVariantSelectionService variants = mock(UserProductVariantSelectionService.class);
        ObjectMapper objectMapper = new ObjectMapper();

        when(json.readArguments("{}", SelectProductVariantAgentToolInput.class)).thenReturn(input);
        when(json.write(any())).thenAnswer(invocation ->
                objectMapper.writeValueAsString(invocation.getArgument(0)));
        when(json.writeArtifact(any())).thenAnswer(invocation ->
                objectMapper.writeValueAsString(invocation.getArgument(0)));
        when(references.requireOffer(context, "offer-size-6-5")).thenReturn(anchor);
        when(anchor.getCanonicalProductKey()).thenReturn("predator-indoor-white-black");
        when(anchor.getLabel()).thenReturn("adidas Predator League Indoor");
        when(profiles.profile(userId)).thenReturn(profile);
        when(variants.select(any(), any())).thenReturn(selection);
        when(exactOffer.key()).thenReturn("offer-size-11");
        when(results.offerReference(exactOffer)).thenReturn(exactReference);

        SelectProductVariantAgentTool tool = new SelectProductVariantAgentTool(
                json, profiles, references, results, variants);

        var result = tool.execute(context, "{}");

        assertThat(result.resultJson())
                .contains("\"exactMatch\":true")
                .contains("\"selectedOfferKey\":\"offer-size-11\"")
                .contains("\"name\":\"Size\",\"value\":\"11\"")
                .contains("\"cartable\":true");
        assertThat(result.safeSummary()).contains("exact requested variant");
        assertThat(result.artifacts()).singleElement().satisfies(artifact -> {
            assertThat(artifact.type()).isEqualTo(AgentArtifactType.OFFER);
            assertThat(artifact.stableKey()).isEqualTo("variant-selection:offer-size-11");
            assertThat(artifact.offerKey()).isEqualTo("offer-size-11");
            assertThat(artifact.canonicalProductKey()).isEqualTo("predator-indoor-white-black");
            assertThat(artifact.payloadJson()).contains("White/Black", "11");
        });

        ArgumentCaptor<SelectUserProductVariantCommand> command =
                ArgumentCaptor.forClass(SelectUserProductVariantCommand.class);
        verify(variants).select(org.mockito.ArgumentMatchers.eq(profile), command.capture());
        assertThat(command.getValue().anchorOfferKey()).isEqualTo("offer-size-6-5");
        assertThat(command.getValue().selectedOptions()).containsExactly(
                new SelectUserProductVariantCommand.SelectedOption("Color", "White/Black"),
                new SelectUserProductVariantCommand.SelectedOption("Size", "11")
        );
        assertThat(command.getValue().preferredOptionName()).isEqualTo("Size");
    }

    @Test
    void doesNotIssueAnOfferReferenceWhenTheSelectionIsNotExact() throws Exception {
        UUID userId = UUID.randomUUID();
        AgentToolExecutionContext context = new AgentToolExecutionContext(
                userId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Select size 11.");
        SelectProductVariantAgentToolInput input = new SelectProductVariantAgentToolInput(
                "offer-size-6-5",
                List.of(new SelectProductVariantAgentToolInput.SelectedOption("Size", "11")),
                "Size"
        );
        AgentArtifactReference anchor = mock(AgentArtifactReference.class);
        AgentJsonSupport json = mock(AgentJsonSupport.class);
        AgentContextProfileService profiles = mock(AgentContextProfileService.class);
        AgentProductReadReferenceService references = mock(AgentProductReadReferenceService.class);
        AgentProductReadResultService results = mock(AgentProductReadResultService.class);
        UserProductVariantSelectionService variants = mock(UserProductVariantSelectionService.class);
        EnsureUserProfileCommand profile = new EnsureUserProfileCommand(
                userId, "shopper@example.test", "Shopper", null);
        ObjectMapper objectMapper = new ObjectMapper();

        when(json.readArguments("{}", SelectProductVariantAgentToolInput.class)).thenReturn(input);
        when(json.write(any())).thenAnswer(invocation ->
                objectMapper.writeValueAsString(invocation.getArgument(0)));
        when(references.requireOffer(context, "offer-size-6-5")).thenReturn(anchor);
        when(anchor.getCanonicalProductKey()).thenReturn("predator-indoor-white-black");
        when(profiles.profile(userId)).thenReturn(profile);
        UserProductVariantSelectionResult selection = new UserProductVariantSelectionResult(
                details("White/Black", "6.5", true), null, false);
        when(variants.select(any(), any())).thenReturn(selection);

        var result = new SelectProductVariantAgentTool(
                json, profiles, references, results, variants).execute(context, "{}");

        assertThat(result.resultJson())
                .contains("\"exactMatch\":false")
                .contains("\"cartable\":false")
                .doesNotContain("selectedOfferKey");
        assertThat(result.artifacts()).isEmpty();
        assertThat(result.safeSummary()).contains("cart was not changed");
    }

    private RehydratedProductDetails details(String color, String size, boolean available) {
        RehydratedProductDetails details = mock(RehydratedProductDetails.class);
        RehydratedProductDetails.Variant variant = mock(RehydratedProductDetails.Variant.class);
        List<RehydratedProductDetails.SelectedOption> selected = List.of(
                new RehydratedProductDetails.SelectedOption("Color", color),
                new RehydratedProductDetails.SelectedOption("Size", size)
        );
        when(details.options()).thenReturn(List.of());
        when(details.variants()).thenReturn(List.of(variant));
        when(details.selected()).thenReturn(selected);
        when(details.selectedVariant()).thenReturn(variant);
        when(details.totalVariants()).thenReturn(1);
        when(variant.title()).thenReturn(color + " / " + size);
        when(variant.available()).thenReturn(available);
        when(variant.selectedOptions()).thenReturn(selected);
        return details;
    }
}
