package com.meant.api.module.agent.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
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
import com.meant.api.module.agent.service.AgentProductSearchQualificationService;
import com.meant.api.module.agent.service.AgentSimilaritySearchQualificationService;
import com.meant.api.module.agent.service.command.BindAgentSimilaritySearchQualificationCommand;
import com.meant.api.module.agent.service.command.QualifyAgentProductSearchCommand;
import com.meant.api.module.agent.service.dto.AgentArtifact;
import com.meant.api.module.agent.service.dto.AgentInventoryProductAnchor;
import com.meant.api.module.agent.service.dto.AgentProductReferenceResult;
import com.meant.api.module.agent.service.dto.AgentProductSearchQualificationResult;
import com.meant.api.module.agent.service.dto.AgentSimilarityAnchorResult;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryFilters;
import com.meant.api.module.user.constant.UserProductSearchQuestionTarget;
import com.meant.api.module.user.service.UserSimilarProductSearchService;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.QualifyUserProductSearchCommand;
import com.meant.api.module.user.service.command.SearchSimilarUserProductsCommand;
import com.meant.api.module.user.service.dto.UserGroupedProductSearchResult;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class FindSimilarProductsAgentToolTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID MERCHANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000303");
    private static final UUID INVENTORY_ITEM_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000302");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentJsonSupport json = new AgentJsonSupport(objectMapper, properties());
    private final AgentContextProfileService profiles = mock(AgentContextProfileService.class);
    private final AgentProductReadReferenceService references = mock(AgentProductReadReferenceService.class);
    private final AgentInventoryProductAnchorService inventoryAnchors =
            mock(AgentInventoryProductAnchorService.class);
    private final AgentProductReadResultService productResults = mock(AgentProductReadResultService.class);
    private final AgentProductSearchQualificationService qualifications =
            mock(AgentProductSearchQualificationService.class);
    private final AgentSimilaritySearchQualificationService similarityQualifications =
            mock(AgentSimilaritySearchQualificationService.class);
    private final UserSimilarProductSearchService searches = mock(UserSimilarProductSearchService.class);
    private final FindSimilarProductsAgentTool tool = new FindSimilarProductsAgentTool(
            json,
            profiles,
            references,
            inventoryAnchors,
            productResults,
            qualifications,
            similarityQualifications,
            searches
    );

    @BeforeEach
    void qualifySearch() {
        when(qualifications.qualify(any())).thenAnswer(invocation -> {
            QualifyAgentProductSearchCommand command = invocation.getArgument(0);
            String effectiveQuery = command.trustedReferenceProductText() != null
                            && command.trustedReferenceProductText().contains("Swim Shorts")
                    ? "more shorts like these"
                    : "similar black jackets";
            return new AgentProductSearchQualificationResult(
                    command.requestQualificationId(),
                    effectiveQuery,
                    "Ready",
                    List.of(),
                    availableOnly()
            );
        });
    }

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

        AgentToolExecutionContext executionContext = context();
        var result = tool.execute(
                executionContext,
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
        assertThat(command.getValue().qualificationId())
                .isEqualTo(expectedQualificationId(executionContext));
        assertThat(command.getValue().merchantId()).isEqualTo(MERCHANT_ID);
        assertThat(command.getValue().buyerIp()).isEqualTo("203.0.113.42");
        assertThat(command.getValue().userAgent()).isEqualTo("Meant Browser/1.0");
        assertThat(command.getValue().language()).isEqualTo("cs-CZ");
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

    @Test
    void bindsTheExactFootwearAnchorAndMakesNoSearchCallUntilSizeAndDestinationAreQualified()
            throws Exception {
        EnsureUserProfileCommand profile = profile();
        AgentArtifactReference productReference = AgentArtifactReference.builder()
                .label("Cool Running Shoes")
                .canonicalProductKey("canonical:running-shoes")
                .build();
        when(profiles.profile(USER_ID)).thenReturn(profile);
        when(references.requireProduct(any(), eq("canonical:running-shoes"))).thenReturn(productReference);
        org.mockito.Mockito.doAnswer(invocation -> {
            QualifyAgentProductSearchCommand command = invocation.getArgument(0);
            return new AgentProductSearchQualificationResult(
                    command.requestQualificationId(),
                    "find similar products",
                    "What shoe size do you need, and what country should it ship to?",
                    List.of(
                            UserProductSearchQuestionTarget.SIZE,
                            UserProductSearchQuestionTarget.SHIPS_TO
                    ),
                    null
            );
        }).when(qualifications).qualify(any());

        AgentToolExecutionContext executionContext = context();
        UUID qualificationId = expectedQualificationId(executionContext);
        var result = tool.execute(
                executionContext,
                "{\"canonicalProductKey\":\"canonical:running-shoes\","
                        + "\"query\":\"find similar products\"}"
        );

        assertThat(result.waitingForUserMessage())
                .isEqualTo("What shoe size do you need, and what country should it ship to?");
        JsonNode output = objectMapper.readTree(result.resultJson());
        assertThat(output.get("qualificationId").asText()).isEqualTo(qualificationId.toString());
        assertThat(output.get("qualificationTargets")).extracting(JsonNode::asText)
                .containsExactly("SIZE", "SHIPS_TO");
        verifyNoInteractions(searches);

        ArgumentCaptor<QualifyAgentProductSearchCommand> qualificationCommand =
                ArgumentCaptor.forClass(QualifyAgentProductSearchCommand.class);
        verify(qualifications).qualify(qualificationCommand.capture());
        assertThat(qualificationCommand.getValue().authoritativeUserText())
                .isEqualTo("check my black jacket in inventory and find me some new that are similar");
        assertThat(qualificationCommand.getValue().trustedReferenceProductText())
                .isEqualTo("Cool Running Shoes");

        ArgumentCaptor<BindAgentSimilaritySearchQualificationCommand> bindingCommand =
                ArgumentCaptor.forClass(BindAgentSimilaritySearchQualificationCommand.class);
        verify(similarityQualifications).bind(bindingCommand.capture());
        assertThat(bindingCommand.getValue().qualificationId()).isEqualTo(qualificationId);
        assertThat(bindingCommand.getValue().canonicalProductKey()).isEqualTo("canonical:running-shoes");
        assertThat(bindingCommand.getValue().anchorLabel()).isEqualTo("Cool Running Shoes");
        var ordering = inOrder(similarityQualifications, qualifications);
        ordering.verify(similarityQualifications).bind(any());
        ordering.verify(qualifications).qualify(any());
    }

    @Test
    void keepsTheExactAnchorReservedWhenQualificationFailsAfterStartingDurableWork() {
        AgentArtifactReference productReference = AgentArtifactReference.builder()
                .label("Cool Running Shoes")
                .canonicalProductKey("canonical:running-shoes")
                .build();
        when(profiles.profile(USER_ID)).thenReturn(profile());
        when(references.requireProduct(any(), eq("canonical:running-shoes"))).thenReturn(productReference);
        org.mockito.Mockito.doThrow(new IllegalStateException("worker stopped after qualification persistence"))
                .when(qualifications)
                .qualify(any());
        AgentToolExecutionContext executionContext = context();

        assertThatThrownBy(() -> tool.execute(
                executionContext,
                "{\"canonicalProductKey\":\"canonical:running-shoes\","
                        + "\"query\":\"find similar products\"}"
        )).isInstanceOf(IllegalStateException.class);

        ArgumentCaptor<BindAgentSimilaritySearchQualificationCommand> bindingCommand =
                ArgumentCaptor.forClass(BindAgentSimilaritySearchQualificationCommand.class);
        verify(similarityQualifications).bind(bindingCommand.capture());
        assertThat(bindingCommand.getValue().qualificationId())
                .isEqualTo(expectedQualificationId(executionContext));
        assertThat(bindingCommand.getValue().canonicalProductKey())
                .isEqualTo("canonical:running-shoes");
        verifyNoInteractions(searches);
    }

    private AgentToolExecutionContext context() {
        return new AgentToolExecutionContext(
                USER_ID,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "check my black jacket in inventory and find me some new that are similar"
        ).withMerchantId(MERCHANT_ID)
                .withBuyerIp("203.0.113.42")
                .withUserAgent("Meant Browser/1.0")
                .withLanguage("cs-CZ");
    }

    private EnsureUserProfileCommand profile() {
        return new EnsureUserProfileCommand(USER_ID, "similarity@example.test", "Similar", "Shopper");
    }

    private UUID expectedQualificationId(AgentToolExecutionContext context) {
        return QualifyUserProductSearchCommand.requestQualificationId(
                USER_ID,
                context.conversationId(),
                MERCHANT_ID,
                context.triggeringMessageId()
        );
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

    private CatalogDiscoveryFilters availableOnly() {
        return new CatalogDiscoveryFilters(
                true,
                List.of(),
                null,
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                null,
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
