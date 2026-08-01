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
import com.meant.api.module.agent.service.dto.AgentProductReferenceResult;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.CompareProductsAgentToolInput;
import com.meant.api.module.agent.service.dto.PickRecommendedProductAgentToolInput;
import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.OfferAvailability;
import com.meant.api.module.catalog.service.dto.OfferAvailabilityStatus;
import com.meant.api.module.user.service.UserCanonicalProductDetailService;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.dto.UserCanonicalProductPersonalizationResult;
import com.meant.api.module.user.service.dto.UserCanonicalProductsRehydrationResult;
import com.meant.api.module.user.service.dto.UserProductDetailResult;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GroundedProductReadAgentToolsTest {

    @Test
    void comparisonKeepsEachProductsPersonalizationInTheModelResult() throws Exception {
        UUID userId = UUID.randomUUID();
        AgentToolExecutionContext context = context(userId);
        EnsureUserProfileCommand profile = profile(userId);
        CanonicalProduct firstProduct = mock(CanonicalProduct.class);
        CanonicalProduct secondProduct = mock(CanonicalProduct.class);
        UserCanonicalProductPersonalizationResult firstPersonalization = personalization("streetwear");
        UserCanonicalProductPersonalizationResult secondPersonalization = personalization("sustainable-brands");
        UserProductDetailResult firstDetail = detail(firstProduct, firstPersonalization);
        UserProductDetailResult secondDetail = detail(secondProduct, secondPersonalization);
        AgentProductReadResultService results = mock(AgentProductReadResultService.class);
        AgentJsonSupport json = json();
        AgentContextProfileService profiles = mock(AgentContextProfileService.class);
        UserCanonicalProductDetailService details = mock(UserCanonicalProductDetailService.class);

        when(json.readArguments("{}", CompareProductsAgentToolInput.class))
                .thenReturn(new CompareProductsAgentToolInput(List.of("first", "second")));
        when(profiles.profile(userId)).thenReturn(profile);
        when(details.rehydrate(any(), any())).thenReturn(new UserCanonicalProductsRehydrationResult(
                List.of(firstDetail, secondDetail),
                List.of()
        ));
        when(results.reference(firstProduct, 1, null, firstPersonalization))
                .thenReturn(reference("first", firstPersonalization));
        when(results.reference(secondProduct, 2, null, secondPersonalization))
                .thenReturn(reference("second", secondPersonalization));
        when(results.detailArtifacts(firstDetail, 1)).thenReturn(List.of());
        when(results.detailArtifacts(secondDetail, 2)).thenReturn(List.of());

        CompareProductsAgentTool tool = new CompareProductsAgentTool(
                json,
                profiles,
                mock(AgentProductReadReferenceService.class),
                results,
                details
        );

        var execution = tool.execute(context, "{}");

        assertThat(execution.resultJson())
                .contains("\"matchedFilterIds\":[\"streetwear\"]")
                .contains("\"matchedFilterIds\":[\"sustainable-brands\"]")
                .contains("\"unknownFilterIds\":[\"no-polyester\"]");
        verify(results).reference(firstProduct, 1, null, firstPersonalization);
        verify(results).reference(secondProduct, 2, null, secondPersonalization);
    }

    @Test
    void recommendedProductKeepsItsPersonalizationInTheModelResult() throws Exception {
        UUID userId = UUID.randomUUID();
        AgentToolExecutionContext context = context(userId);
        EnsureUserProfileCommand profile = profile(userId);
        CanonicalProduct product = mock(CanonicalProduct.class);
        Offer offer = mock(Offer.class);
        UserCanonicalProductPersonalizationResult personalization = personalization("streetwear");
        UserProductDetailResult detail = detail(product, personalization);
        AgentProductReadResultService results = mock(AgentProductReadResultService.class);
        AgentJsonSupport json = json();
        AgentContextProfileService profiles = mock(AgentContextProfileService.class);
        UserCanonicalProductDetailService details = mock(UserCanonicalProductDetailService.class);

        when(json.readArguments("{}", PickRecommendedProductAgentToolInput.class))
                .thenReturn(new PickRecommendedProductAgentToolInput(List.of("recommended")));
        when(profiles.profile(userId)).thenReturn(profile);
        when(product.offers()).thenReturn(List.of(offer));
        when(offer.availability()).thenReturn(new OfferAvailability(
                OfferAvailabilityStatus.IN_STOCK,
                null,
                null
        ));
        when(details.rehydrate(any(), any())).thenReturn(new UserCanonicalProductsRehydrationResult(
                List.of(detail),
                List.of()
        ));
        when(results.reference(product, 1, null, personalization))
                .thenReturn(reference("recommended", personalization));
        when(results.detailArtifacts(detail, 1)).thenReturn(List.of());

        PickRecommendedProductAgentTool tool = new PickRecommendedProductAgentTool(
                json,
                profiles,
                mock(AgentProductReadReferenceService.class),
                results,
                details
        );

        var execution = tool.execute(context, "{}");

        assertThat(execution.resultJson())
                .contains("\"matchedFilterIds\":[\"streetwear\"]")
                .contains("\"unknownFilterIds\":[\"no-polyester\"]")
                .contains("\"hardConstraintFilterIds\":[\"no-polyester\"]");
        verify(results).reference(product, 1, null, personalization);
    }

    private AgentJsonSupport json() throws Exception {
        AgentJsonSupport json = mock(AgentJsonSupport.class);
        when(json.write(any())).thenAnswer(invocation ->
                new tools.jackson.databind.ObjectMapper().writeValueAsString(invocation.getArgument(0)));
        when(json.writeArtifact(any())).thenReturn("{}");
        return json;
    }

    private AgentToolExecutionContext context(UUID userId) {
        return new AgentToolExecutionContext(
                userId,
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                "Explain which result best fits my saved preferences."
        );
    }

    private EnsureUserProfileCommand profile(UUID userId) {
        return new EnsureUserProfileCommand(userId, "shopper@example.test", "Shopper", null);
    }

    private UserProductDetailResult detail(
            CanonicalProduct product,
            UserCanonicalProductPersonalizationResult personalization
    ) {
        return new UserProductDetailResult(
                product,
                null,
                null,
                null,
                personalization,
                Map.of(),
                Map.of(),
                List.of()
        );
    }

    private UserCanonicalProductPersonalizationResult personalization(String matchedFilterId) {
        return new UserCanonicalProductPersonalizationResult(
                "Matched: " + matchedFilterId + ". Unknown: No polyester.",
                List.of(matchedFilterId),
                List.of(),
                List.of("no-polyester"),
                List.of("no-polyester")
        );
    }

    private AgentProductReferenceResult reference(
            String key,
            UserCanonicalProductPersonalizationResult personalization
    ) {
        return new AgentProductReferenceResult(
                1,
                key,
                key,
                null,
                null,
                null,
                List.of(),
                personalization,
                null
        );
    }
}
