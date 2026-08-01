package com.meant.api.module.agent.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.entity.AgentArtifactReference;
import com.meant.api.module.agent.service.AgentContextProfileService;
import com.meant.api.module.agent.service.AgentInventoryProductAnchorService;
import com.meant.api.module.agent.service.AgentJsonSupport;
import com.meant.api.module.agent.service.AgentProductReadReferenceService;
import com.meant.api.module.agent.service.AgentProductReadResultService;
import com.meant.api.module.agent.service.AgentProductSearchQualificationService;
import com.meant.api.module.agent.service.AgentSimilaritySearchQualificationService;
import com.meant.api.module.agent.service.command.QualifyAgentProductSearchCommand;
import com.meant.api.module.agent.service.dto.AgentProductReferenceResult;
import com.meant.api.module.agent.service.dto.AgentProductSearchQualificationResult;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.FindSimilarProductsAgentToolInput;
import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryFilters;
import com.meant.api.module.user.service.UserSimilarProductSearchService;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.dto.UserCanonicalProductPersonalizationResult;
import com.meant.api.module.user.service.dto.UserGroupedProductSearchResult;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FindSimilarProductsAgentToolTest {

    @Test
    void descriptorExposesStableAnchorsAndAdvisorMetadataWithoutContinuationTokens() {
        FindSimilarProductsAgentTool tool = new FindSimilarProductsAgentTool(
                mock(AgentJsonSupport.class),
                mock(AgentContextProfileService.class),
                mock(AgentProductReadReferenceService.class),
                mock(AgentInventoryProductAnchorService.class),
                mock(AgentProductReadResultService.class),
                mock(AgentProductSearchQualificationService.class),
                mock(AgentSimilaritySearchQualificationService.class),
                mock(UserSimilarProductSearchService.class)
        );

        assertThat(tool.descriptor().inputSchemaJson())
                .contains("canonicalProductKey", "inventoryItemId")
                .doesNotContain("qualificationId", "updatedAt");
        assertThat(tool.descriptor().description())
                .contains("appliedFilters", "unsetFilters");
    }

    @Test
    void directActionUsesItsStableRequestWithoutLookingUpANonexistentLedgerMessage() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID actionId = UUID.randomUUID();
        UUID syntheticTriggerId = UUID.randomUUID();
        String canonicalProductKey = "product:predator-league";
        AgentJsonSupport json = mock(AgentJsonSupport.class);
        AgentContextProfileService profiles = mock(AgentContextProfileService.class);
        AgentProductReadReferenceService references = mock(AgentProductReadReferenceService.class);
        AgentProductSearchQualificationService qualifications =
                mock(AgentProductSearchQualificationService.class);
        AgentSimilaritySearchQualificationService similarityQualifications =
                mock(AgentSimilaritySearchQualificationService.class);
        AgentProductReadResultService results = mock(AgentProductReadResultService.class);
        UserSimilarProductSearchService searches = mock(UserSimilarProductSearchService.class);
        EnsureUserProfileCommand profile = new EnsureUserProfileCommand(
                userId, "shopper@example.test", "Shopper", null);
        AgentArtifactReference reference = mock(AgentArtifactReference.class);
        CanonicalProduct product = mock(CanonicalProduct.class);
        UserCanonicalProductPersonalizationResult personalization =
                new UserCanonicalProductPersonalizationResult(
                        "Unknown: No polyester.",
                        List.of("streetwear"),
                        List.of(),
                        List.of("no-polyester"),
                        List.of("no-polyester")
                );
        AgentProductReferenceResult modelReference = new AgentProductReferenceResult(
                1,
                "similar-product",
                "Similar streetwear tee",
                null,
                null,
                null,
                List.of(),
                personalization,
                null
        );

        when(json.readArguments("{}", FindSimilarProductsAgentToolInput.class))
                .thenReturn(new FindSimilarProductsAgentToolInput(
                        canonicalProductKey, null, "products similar to Predator League"));
        when(json.write(any())).thenAnswer(invocation ->
                new tools.jackson.databind.ObjectMapper().writeValueAsString(invocation.getArgument(0)));
        when(profiles.profile(userId)).thenReturn(profile);
        when(references.requireProduct(any(), eq(canonicalProductKey))).thenReturn(reference);
        when(reference.getLabel()).thenReturn("adidas Predator League");
        when(product.key()).thenReturn("similar-product");
        when(results.reference(product, 1, null, personalization)).thenReturn(modelReference);
        when(results.discoveryArtifacts(any(), any(Integer.class), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(qualifications.qualify(any())).thenAnswer(invocation -> {
            QualifyAgentProductSearchCommand command = invocation.getArgument(0);
            return new AgentProductSearchQualificationResult(
                    command.requestQualificationId(),
                    "products similar to Predator League",
                    mock(CatalogDiscoveryFilters.class),
                    Map.of(),
                    List.of(),
                    Set.of(),
                    Set.of()
            );
        });
        when(searches.search(eq(profile), any(), any(), eq(Set.of()), eq(Set.of())))
                .thenReturn(new UserGroupedProductSearchResult(
                        "products similar to Predator League",
                        "products similar to predator league",
                        "profile",
                        false,
                        0,
                        20,
                        null,
                        false,
                        false,
                        List.of(product),
                        Map.of(),
                        Map.of(),
                        Map.of("similar-product", personalization),
                        List.of(),
                        0,
                        false,
                        List.of()
                ));

        FindSimilarProductsAgentTool tool = new FindSimilarProductsAgentTool(
                json,
                profiles,
                references,
                mock(AgentInventoryProductAnchorService.class),
                results,
                qualifications,
                similarityQualifications,
                searches
        );
        AgentToolExecutionContext context = new AgentToolExecutionContext(
                userId,
                conversationId,
                null,
                syntheticTriggerId,
                "Found products similar to adidas Predator League",
                actionId
        );

        var execution = tool.execute(context, "{}");

        ArgumentCaptor<QualifyAgentProductSearchCommand> command =
                ArgumentCaptor.forClass(QualifyAgentProductSearchCommand.class);
        verify(qualifications).qualify(command.capture());
        assertThat(command.getValue().contextMessageId()).isNull();
        assertThat(command.getValue().requestId()).isEqualTo(actionId);
        assertThat(command.getValue().requestQualificationId()).isNotNull();
        assertThat(command.getValue().authoritativeUserText())
                .isEqualTo("products similar to Predator League");
        assertThat(execution.resultJson())
                .contains("\"matchedFilterIds\":[\"streetwear\"]")
                .contains("\"unknownFilterIds\":[\"no-polyester\"]")
                .contains("\"hardConstraintFilterIds\":[\"no-polyester\"]");
        verify(similarityQualifications).bind(any());
        verify(results).reference(product, 1, null, personalization);
    }
}
