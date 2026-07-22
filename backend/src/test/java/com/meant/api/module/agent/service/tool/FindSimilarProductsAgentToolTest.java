package com.meant.api.module.agent.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.entity.AgentArtifactReference;
import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.agent.service.AgentContextProfileService;
import com.meant.api.module.agent.service.AgentInventoryProductAnchorService;
import com.meant.api.module.agent.service.AgentJsonSupport;
import com.meant.api.module.agent.service.AgentProductReadReferenceService;
import com.meant.api.module.agent.service.AgentProductReadResultService;
import com.meant.api.module.agent.service.dto.AgentArtifact;
import com.meant.api.module.agent.service.dto.AgentInventoryProductAnchor;
import com.meant.api.module.agent.service.dto.AgentProductReferenceResult;
import com.meant.api.module.agent.service.dto.AgentSimilarityAnchorResult;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.user.service.UserSimilarProductSearchService;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.SearchSimilarUserProductsCommand;
import com.meant.api.module.user.service.dto.UserGroupedProductSearchResult;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class FindSimilarProductsAgentToolTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID INVENTORY_ITEM_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000302");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentJsonSupport json = new AgentJsonSupport(objectMapper, properties());
    private final AgentContextProfileService profiles = mock(AgentContextProfileService.class);
    private final AgentProductReadReferenceService references = mock(AgentProductReadReferenceService.class);
    private final AgentInventoryProductAnchorService inventoryAnchors =
            mock(AgentInventoryProductAnchorService.class);
    private final AgentProductReadResultService productResults = mock(AgentProductReadResultService.class);
    private final UserSimilarProductSearchService searches = mock(UserSimilarProductSearchService.class);
    private final FindSimilarProductsAgentTool tool = new FindSimilarProductsAgentTool(
            json,
            profiles,
            references,
            inventoryAnchors,
            productResults,
            searches
    );

    @Test
    void inventorySimilarityResultCarriesTheExactResolvedInventoryAnchor() throws Exception {
        EnsureUserProfileCommand profile = profile();
        String verifiedPayload = "{\"name\":\"My waxed black jacket\",\"color\":\"Black\"}";
        AgentArtifactReference inventoryReference = AgentArtifactReference.builder()
                .stableKey("inventory:" + INVENTORY_ITEM_ID)
                .label("My waxed black jacket")
                .canonicalProductKey("canonical:owned-black-jacket")
                .offerKey("offer:owned-black-jacket")
                .inventoryItemId(INVENTORY_ITEM_ID)
                .payloadJson(verifiedPayload)
                .build();
        CanonicalProduct similarProduct = mock(CanonicalProduct.class);
        when(similarProduct.key()).thenReturn("canonical:similar-jacket");
        AgentArtifact productArtifact = new AgentArtifact(
                AgentArtifactType.PRODUCT,
                1,
                "canonical:similar-jacket",
                "Similar jacket",
                "canonical:similar-jacket",
                null,
                null,
                null,
                null,
                null,
                "{}"
        );
        when(profiles.profile(USER_ID)).thenReturn(profile);
        when(references.requireSoleInventoryItem(any(), eq(INVENTORY_ITEM_ID))).thenReturn(inventoryReference);
        when(inventoryAnchors.anchor(USER_ID, INVENTORY_ITEM_ID))
                .thenReturn(new AgentInventoryProductAnchor(
                        "canonical:owned-black-jacket",
                        "Current catalog jacket title"
                ));
        when(searches.search(eq(profile), any()))
                .thenReturn(result("similar black jackets", List.of(similarProduct)));
        when(productResults.reference(similarProduct, 1)).thenReturn(new AgentProductReferenceResult(
                1,
                "canonical:similar-jacket",
                "Similar jacket",
                null,
                null,
                null,
                List.of()
        ));
        when(productResults.discoveryArtifacts(
                eq(similarProduct),
                eq(1),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(List.of(productArtifact));

        var result = tool.execute(
                context(),
                "{\"inventoryItemId\":\"" + INVENTORY_ITEM_ID
                        + "\",\"query\":\"similar black jackets\"}"
        );

        JsonNode anchor = objectMapper.readTree(result.resultJson()).get("similarityAnchor");
        assertThat(anchor.get("canonicalProductKey").asText()).isEqualTo("canonical:owned-black-jacket");
        assertThat(anchor.get("inventoryItemId").asText()).isEqualTo(INVENTORY_ITEM_ID.toString());
        assertThat(anchor.get("label").asText()).isEqualTo("My waxed black jacket");
        assertThat(anchor.get("query").asText()).isEqualTo("similar black jackets");
        assertThat(result.artifacts()).extracting(AgentArtifact::type)
                .containsExactly(AgentArtifactType.PRODUCT);
        assertThat(result.artifacts().getFirst()).isEqualTo(productArtifact);
        assertThat(tool.descriptor().description())
                .contains("unambiguous match")
                .contains("inventoryItemId");

        ArgumentCaptor<SearchSimilarUserProductsCommand> command =
                ArgumentCaptor.forClass(SearchSimilarUserProductsCommand.class);
        verify(searches).search(eq(profile), command.capture());
        assertThat(command.getValue().canonicalProductKey()).isEqualTo("canonical:owned-black-jacket");
        assertThat(command.getValue().query()).isEqualTo("similar black jackets");
    }

    @Test
    void keepsTheVerifiedInventoryAnchorWhenTheModelResultIsTruncated() throws Exception {
        EnsureUserProfileCommand profile = profile();
        AgentArtifactReference inventoryReference = AgentArtifactReference.builder()
                .label("My waxed black jacket")
                .canonicalProductKey(null)
                .offerKey("offer:owned-black-jacket")
                .inventoryItemId(INVENTORY_ITEM_ID)
                .payloadJson("{}")
                .build();
        CanonicalProduct similarProduct = mock(CanonicalProduct.class);
        when(similarProduct.key()).thenReturn("canonical:similar-jacket");
        when(profiles.profile(USER_ID)).thenReturn(profile);
        when(references.requireSoleInventoryItem(any(), eq(INVENTORY_ITEM_ID))).thenReturn(inventoryReference);
        when(inventoryAnchors.anchor(USER_ID, INVENTORY_ITEM_ID))
                .thenReturn(new AgentInventoryProductAnchor(
                        "inventory-product:" + INVENTORY_ITEM_ID,
                        "Current catalog jacket title"
                ));
        when(searches.search(eq(profile), any()))
                .thenReturn(result("similar black jackets", List.of(similarProduct)));
        when(productResults.reference(similarProduct, 1)).thenReturn(new AgentProductReferenceResult(
                1,
                "canonical:similar-jacket",
                "Oversized similar jacket ".repeat(600),
                null,
                null,
                null,
                List.of()
        ));
        when(productResults.discoveryArtifacts(
                eq(similarProduct),
                eq(1),
                any(),
                any(),
                any(),
                any()
        )).thenAnswer(invocation -> {
            AgentSimilarityAnchorResult anchor = invocation.getArgument(5);
            return List.of(new AgentArtifact(
                    AgentArtifactType.PRODUCT,
                    1,
                    "canonical:similar-jacket",
                    "Similar jacket",
                    "canonical:similar-jacket",
                    null,
                    null,
                    null,
                    null,
                    null,
                    objectMapper.writeValueAsString(Map.of(
                            "product", Map.of(),
                            "similarityAnchor", anchor
                    ))
            ));
        });

        var result = tool.execute(
                context(),
                "{\"inventoryItemId\":\"" + INVENTORY_ITEM_ID
                        + "\",\"query\":\"similar black jackets\"}"
        );

        JsonNode modelResult = objectMapper.readTree(result.resultJson());
        assertThat(modelResult.get("truncated").asBoolean()).isTrue();
        assertThat(modelResult.has("similarityAnchor")).isFalse();
        assertThat(result.artifacts()).singleElement().satisfies(artifact -> {
            assertThat(artifact.type()).isEqualTo(AgentArtifactType.PRODUCT);
            JsonNode durableAnchor = objectMapper.readTree(artifact.payloadJson()).get("similarityAnchor");
            assertThat(durableAnchor.get("canonicalProductKey").asText())
                    .isEqualTo("inventory-product:" + INVENTORY_ITEM_ID);
            assertThat(durableAnchor.get("inventoryItemId").asText()).isEqualTo(INVENTORY_ITEM_ID.toString());
            assertThat(durableAnchor.get("label").asText()).isEqualTo("My waxed black jacket");
        });
    }

    @Test
    void canonicalProductSimilarityResultCarriesTheVerifiedProductLabel() throws Exception {
        EnsureUserProfileCommand profile = profile();
        AgentArtifactReference productReference = AgentArtifactReference.builder()
                .label("Swim Shorts with Packing Pouch")
                .canonicalProductKey("canonical:swim-shorts")
                .build();
        when(profiles.profile(USER_ID)).thenReturn(profile);
        when(references.requireProduct(any(), eq("canonical:swim-shorts"))).thenReturn(productReference);
        when(searches.search(eq(profile), any())).thenReturn(emptyResult("more shorts like these"));

        var result = tool.execute(
                context(),
                "{\"canonicalProductKey\":\"canonical:swim-shorts\","
                        + "\"query\":\"more shorts like these\"}"
        );

        JsonNode anchor = objectMapper.readTree(result.resultJson()).get("similarityAnchor");
        assertThat(anchor.get("canonicalProductKey").asText()).isEqualTo("canonical:swim-shorts");
        assertThat(anchor.get("inventoryItemId").isNull()).isTrue();
        assertThat(anchor.get("label").asText()).isEqualTo("Swim Shorts with Packing Pouch");
        assertThat(anchor.get("query").asText()).isEqualTo("more shorts like these");
        assertThat(result.artifacts()).isEmpty();
        verifyNoInteractions(inventoryAnchors);
    }

    private AgentToolExecutionContext context() {
        return new AgentToolExecutionContext(
                USER_ID,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "check my black jacket in inventory and find me some new that are similar"
        );
    }

    private EnsureUserProfileCommand profile() {
        return new EnsureUserProfileCommand(USER_ID, "similarity@example.test", "Similar", "Shopper");
    }

    private UserGroupedProductSearchResult emptyResult(String query) {
        return result(query, List.of());
    }

    private UserGroupedProductSearchResult result(String query, List<CanonicalProduct> products) {
        return new UserGroupedProductSearchResult(
                query,
                query,
                "profile-hash",
                false,
                0,
                20,
                null,
                false,
                false,
                products,
                0,
                false,
                List.of()
        );
    }

    private AgentProperties properties() {
        return new AgentProperties(
                true, "model", "fallback", "https://example.test", "key", "Meant",
                "https://example.test", "v1", "v1", 0, 1000, 8, 20, 5, 4, 40,
                64000, 10000, 2, Duration.ofSeconds(30), Duration.ofSeconds(10),
                Duration.ofSeconds(10), Duration.ofSeconds(10), Duration.ofMillis(10), 128,
                Duration.ofDays(1), Duration.ofMinutes(5)
        );
    }
}
