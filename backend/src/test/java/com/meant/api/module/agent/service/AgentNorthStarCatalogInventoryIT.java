package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.PostgresIntegrationTestSupport;
import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.constant.AgentMessageRole;
import com.meant.api.module.agent.constant.AgentRunStatus;
import com.meant.api.module.agent.constant.AgentToolInvocationStatus;
import com.meant.api.module.agent.repository.AgentArtifactReferenceRepository;
import com.meant.api.module.agent.repository.AgentMessageRepository;
import com.meant.api.module.agent.repository.AgentRunRepository;
import com.meant.api.module.agent.repository.AgentToolInvocationRepository;
import com.meant.api.module.agent.service.command.CreateAgentConversationCommand;
import com.meant.api.module.agent.service.command.SubmitAgentTurnCommand;
import com.meant.api.module.agent.service.command.VisibleProductContextCommand;
import com.meant.api.module.agent.service.dto.AgentModelResponse;
import com.meant.api.module.agent.service.dto.AgentModelToolCall;
import com.meant.api.module.agent.service.dto.AgentModelUsage;
import com.meant.api.module.agent.service.port.AgentModelGateway;
import com.meant.api.module.cart.service.CartService;
import com.meant.api.module.cart.service.command.CreateCartCommand;
import com.meant.api.module.cart.service.dto.CartLineResult;
import com.meant.api.module.cart.service.dto.CartOfferPartitionResult;
import com.meant.api.module.cart.service.dto.CartResult;
import com.meant.api.module.cart.service.query.PartitionSelectedOffersQuery;
import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.Money;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.OfferAvailability;
import com.meant.api.module.catalog.service.dto.OfferAvailabilityStatus;
import com.meant.api.module.catalog.service.dto.OfferIdentity;
import com.meant.api.module.catalog.service.dto.OfferMerchantScope;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.dto.ResultFreshness;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.module.catalog.service.dto.ResultSourceReference;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import com.meant.api.module.user.constant.UserInventoryCategory;
import com.meant.api.module.user.constant.UserInventorySource;
import com.meant.api.module.user.entity.User;
import com.meant.api.module.user.repository.UserRepository;
import com.meant.api.module.user.service.UserCanonicalProductReferencePersistenceService;
import com.meant.api.module.user.service.UserCommerceContextService;
import com.meant.api.module.user.service.UserGroupedProductSearchService;
import com.meant.api.module.user.service.UserInventoryProductRehydrationService;
import com.meant.api.module.user.service.UserInventoryService;
import com.meant.api.module.user.service.UserSimilarProductSearchService;
import com.meant.api.module.user.service.command.SearchSimilarUserProductsCommand;
import com.meant.api.module.user.service.command.SearchUserProductsCommand;
import com.meant.api.module.user.service.dto.UserCommerceContextResult;
import com.meant.api.module.user.service.dto.UserGroupedProductSearchResult;
import com.meant.api.module.user.service.dto.UserInventoryCommerceReference;
import com.meant.api.module.user.service.dto.UserInventoryItemResult;
import com.meant.api.module.user.service.dto.UserInventoryProductRehydrationResult;
import com.meant.api.module.user.service.dto.UserInventorySelectedOption;
import com.meant.api.module.user.service.query.ListUserInventoryItemsQuery;
import com.meant.api.module.user.service.query.RehydrateUserInventoryProductQuery;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "commerce.agent.enabled=true",
        "commerce.agent.api-key=test-key",
        "commerce.agent.event-poll-interval=10ms"
})
class AgentNorthStarCatalogInventoryIT extends PostgresIntegrationTestSupport {

    private static final UUID CLOTHING_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000911");
    private static final UUID SHOES_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000912");
    private static final UUID INVENTORY_ITEM_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000913");
    private static final UUID CLARIFICATION_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000914");
    private static final ProviderIdentity PROVIDER = new ProviderIdentity("SHOPIFY");

    @Autowired private UserRepository userRepository;
    @Autowired private AgentConversationService conversationService;
    @Autowired private AgentTurnService turnService;
    @Autowired private AgentRunCoordinator coordinator;
    @Autowired private AgentRunRepository runRepository;
    @Autowired private AgentToolInvocationRepository invocationRepository;
    @Autowired private AgentArtifactReferenceRepository artifactRepository;
    @Autowired private AgentMessageRepository messageRepository;

    @MockitoBean private AgentModelGateway modelGateway;
    @MockitoBean private UserGroupedProductSearchService catalogSearchService;
    @MockitoBean private UserInventoryService inventoryService;
    @MockitoBean private UserInventoryProductRehydrationService inventoryRehydrationService;
    @MockitoBean private UserCanonicalProductReferencePersistenceService productReferencePersistenceService;
    @MockitoBean private UserSimilarProductSearchService similarProductSearchService;
    @MockitoBean private UserCommerceContextService commerceContextService;
    @MockitoBean private CartService cartService;

    @Test
    void broadClothingSearchAddsTheThirdVisibleProductFromTheSecondPageAfterAConversationalLeadIn()
            throws Exception {
        persistUser(CLOTHING_USER_ID, "north-star-clothing@example.test");
        List<CanonicalProduct> products = List.of(
                product("linen-shirt", "Linen shirt", "M"),
                product("straight-jeans", "Straight jeans", "M"),
                product("cotton-jacket", "Cotton jacket", "M"),
                product("camo-hat", "Camo trucker hat", "M"),
                product("canvas-cap", "Canvas cap", "M"),
                product("mesh-cap", "Mesh cap", "M"),
                product("duck-camo-cap", "Duck camo cap", "M"),
                product("embroidered-cap", "Embroidered cap", "M")
        );
        String thirdVisibleOfferKey = products.get(6).offers().getFirst().key();
        when(catalogSearchService.search(any(), any(SearchUserProductsCommand.class)))
                .thenReturn(searchResult("versatile new clothing", products));
        stubCart(thirdVisibleOfferKey, CLOTHING_USER_ID);
        scriptModel(
                tool("a-search", "search_catalog", "{\"query\":\"versatile new clothing\",\"limit\":8}"),
                text("Here is a useful starting set."),
                tool("a-cart", "prepare_carts",
                        "{\"offers\":[{\"offerKey\":\"" + thirdVisibleOfferKey
                                + "\",\"quantity\":1}]}"),
                text("The selected item is in your cart.")
        );

        UUID conversationId = conversationService.create(new CreateAgentConversationCommand(
                CLOTHING_USER_ID, "North-star clothing")).conversationId();
        UUID searchRunId = runTurn(
                CLOTHING_USER_ID, conversationId, "I wanna buy new clothes.", "north-star-a-search");
        List<com.meant.api.module.agent.entity.AgentArtifactReference> searchProducts =
                artifactRepository.findByRunIdOrderByCreatedAtAscOrdinalAsc(searchRunId).stream()
                        .filter(artifact -> artifact.getArtifactType() == AgentArtifactType.PRODUCT)
                        .toList();
        VisibleProductContextCommand secondPage = new VisibleProductContextCommand(
                searchProducts.getFirst().getMessageId(),
                products.subList(4, 8).stream().map(CanonicalProduct::key).toList()
        );
        UUID cartRunId = runTurn(
                CLOTHING_USER_ID,
                conversationId,
                "ok looks good, add the third one into cart",
                "north-star-a-cart",
                secondPage
        );

        assertThat(invocations(searchRunId))
                .singleElement()
                .satisfies(invocation -> {
                    assertThat(invocation.getToolName()).isEqualTo("search_catalog");
                    assertThat(invocation.getStatus()).isEqualTo(AgentToolInvocationStatus.COMPLETED);
                });
        assertThat(searchProducts).extracting(com.meant.api.module.agent.entity.AgentArtifactReference::getOrdinal)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
        assertThat(searchProducts)
                .extracting(com.meant.api.module.agent.entity.AgentArtifactReference::getCanonicalProductKey)
                .containsExactlyElementsOf(products.stream().map(CanonicalProduct::key).toList());
        assertThat(invocations(cartRunId))
                .singleElement()
                .satisfies(invocation -> {
                    assertThat(invocation.getToolName()).isEqualTo("prepare_carts");
                    assertThat(invocation.getStatus()).isEqualTo(AgentToolInvocationStatus.COMPLETED);
                    assertThat(invocation.getArgumentsJson()).contains(thirdVisibleOfferKey);
                });

        ArgumentCaptor<PartitionSelectedOffersQuery> partition =
                ArgumentCaptor.forClass(PartitionSelectedOffersQuery.class);
        verify(cartService).partitionSelectedOffers(partition.capture());
        assertThat(partition.getValue().userId()).isEqualTo(CLOTHING_USER_ID);
        assertThat(partition.getValue().items())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.offerKey()).isEqualTo(thirdVisibleOfferKey);
                    assertThat(item.quantity()).isEqualTo(1);
                });
        ArgumentCaptor<CreateCartCommand> create = ArgumentCaptor.forClass(CreateCartCommand.class);
        verify(cartService).create(create.capture(), any(UUID.class));
        assertThat(create.getValue().addItems())
                .singleElement()
                .extracting(CreateCartCommand.AddItem::offerKey)
                .isEqualTo(thirdVisibleOfferKey);
        assertThat(artifactRepository.findByRunIdOrderByCreatedAtAscOrdinalAsc(cartRunId))
                .extracting(artifact -> artifact.getArtifactType())
                .contains(AgentArtifactType.CART, AgentArtifactType.CART_LINE);
    }

    @Test
    void conflictingVisibleOrdinalAndDescriptionWaitsForAChoiceThenCartsTheExactNumericReply()
            throws Exception {
        persistUser(CLARIFICATION_USER_ID, "north-star-clarification@example.test");
        List<CanonicalProduct> products = List.of(
                product("blue-linen-cap", "Blue linen cap", "M"),
                product("blue-mesh-cap", "Blue mesh cap", "M"),
                product("red-wool-cap", "Red wool cap", "M"),
                product("green-canvas-cap", "Green canvas cap", "M")
        );
        String thirdOfferKey = products.get(2).offers().getFirst().key();
        when(catalogSearchService.search(any(), any(SearchUserProductsCommand.class)))
                .thenReturn(searchResult("caps", products));
        stubCart(thirdOfferKey, CLARIFICATION_USER_ID);
        scriptModel(
                tool("clarify-search", "search_catalog", "{\"query\":\"caps\",\"limit\":4}"),
                text("Here are four cap options:"),
                tool("clarify-cart", "prepare_carts",
                        "{\"offers\":[{\"offerKey\":\"" + thirdOfferKey
                                + "\",\"quantity\":1}]}"),
                text("The red wool cap is in your cart.")
        );

        UUID conversationId = conversationService.create(new CreateAgentConversationCommand(
                CLARIFICATION_USER_ID, "North-star product clarification")).conversationId();
        UUID searchRunId = runTurn(
                CLARIFICATION_USER_ID,
                conversationId,
                "Show me some caps.",
                "north-star-clarification-search"
        );
        List<com.meant.api.module.agent.entity.AgentArtifactReference> searchProducts =
                artifactRepository.findByRunIdOrderByCreatedAtAscOrdinalAsc(searchRunId).stream()
                        .filter(artifact -> artifact.getArtifactType() == AgentArtifactType.PRODUCT)
                        .toList();
        VisibleProductContextCommand visibleProducts = new VisibleProductContextCommand(
                searchProducts.getFirst().getMessageId(),
                products.stream().map(CanonicalProduct::key).toList()
        );

        UUID clarificationRunId = runTurnExpecting(
                CLARIFICATION_USER_ID,
                conversationId,
                "Add the third blue one to my cart.",
                "north-star-clarification-conflict",
                visibleProducts,
                AgentRunStatus.WAITING_FOR_USER
        );

        assertThat(invocations(clarificationRunId)).isEmpty();
        verify(cartService, never()).partitionSelectedOffers(any(PartitionSelectedOffersQuery.class));
        verify(cartService, never()).create(any(CreateCartCommand.class), any(UUID.class));
        verify(modelGateway, times(2)).turn(any(), any(), any());
        assertThat(messageRepository.findByRunIdOrderBySequenceNumberAsc(clarificationRunId))
                .filteredOn(message -> message.getRole() == AgentMessageRole.ASSISTANT)
                .singleElement()
                .satisfies(message -> {
                    assertThat(message.getTextContent())
                            .startsWith("Which product should I add to your cart?")
                            .contains("Reply with a number or product name")
                            .containsSubsequence(
                                    "1. Blue linen cap",
                                    "2. Blue mesh cap",
                                    "3. Red wool cap",
                                    "4. Green canvas cap")
                            .doesNotContain("I'm sorry");
                    assertThat(message.getContentJson())
                            .contains("pendingProductClarification", "prepare_carts", thirdOfferKey);
                });

        UUID cartRunId = runTurn(
                CLARIFICATION_USER_ID,
                conversationId,
                "3.",
                "north-star-clarification-answer"
        );

        assertThat(invocations(cartRunId))
                .singleElement()
                .satisfies(invocation -> {
                    assertThat(invocation.getToolName()).isEqualTo("prepare_carts");
                    assertThat(invocation.getStatus()).isEqualTo(AgentToolInvocationStatus.COMPLETED);
                    assertThat(invocation.getArgumentsJson()).contains(thirdOfferKey);
                });
        ArgumentCaptor<PartitionSelectedOffersQuery> partition =
                ArgumentCaptor.forClass(PartitionSelectedOffersQuery.class);
        verify(cartService).partitionSelectedOffers(partition.capture());
        assertThat(partition.getValue().items())
                .singleElement()
                .extracting(PartitionSelectedOffersQuery.Item::offerKey)
                .isEqualTo(thirdOfferKey);
        ArgumentCaptor<CreateCartCommand> create = ArgumentCaptor.forClass(CreateCartCommand.class);
        verify(cartService).create(create.capture(), any(UUID.class));
        assertThat(create.getValue().addItems())
                .singleElement()
                .extracting(CreateCartCommand.AddItem::offerKey)
                .isEqualTo(thirdOfferKey);
        assertThat(runRepository.findById(cartRunId).orElseThrow().getStatus())
                .isEqualTo(AgentRunStatus.COMPLETED);
        verify(modelGateway, times(4)).turn(any(), any(), any());
    }

    @Test
    void ownedShoesPreserveSizeThroughSimilarityAndBindTheExactFirstOfferForCart() throws Exception {
        persistUser(SHOES_USER_ID, "north-star-shoes@example.test");
        UserInventoryCommerceReference commerceReference = new UserInventoryCommerceReference(
                "SHOPIFY",
                null,
                "merchant-shoes",
                "shoes.example.test",
                "canonical:owned-trail-shoe",
                "offer:owned-trail-shoe-size-42",
                "PROVIDER_CATALOG",
                "GLOBAL_CATALOG",
                "gid://shopify/Product/owned-trail-shoe",
                "gid://shopify/ProductVariant/owned-trail-shoe-42",
                List.of(new UserInventorySelectedOption("variant", "Size", "42"))
        );
        UserInventoryItemResult ownedShoes = inventoryItem(commerceReference);
        when(inventoryService.list(any(), any(ListUserInventoryItemsQuery.class)))
                .thenAnswer(invocation -> {
                    ListUserInventoryItemsQuery query = invocation.getArgument(1);
                    return query.page() == 0 ? List.of(ownedShoes) : List.of();
                });
        when(commerceContextService.find(SHOES_USER_ID)).thenReturn(new UserCommerceContextResult("US"));
        when(inventoryRehydrationService.rehydrate(any(RehydrateUserInventoryProductQuery.class)))
                .thenReturn(new UserInventoryProductRehydrationResult(
                        INVENTORY_ITEM_ID,
                        commerceReference,
                        null,
                        "Owned trail shoes",
                        UserInventoryCategory.APPAREL,
                        null,
                        "42",
                        "Blue",
                        "Mesh",
                        null
                ));
        List<CanonicalProduct> similarProducts = List.of(
                product("similar-trail-shoe", "Similar trail shoe", "42"),
                product("similar-road-shoe", "Similar road shoe", "42")
        );
        String firstOfferKey = similarProducts.getFirst().offers().getFirst().key();
        String secondOfferKey = similarProducts.get(1).offers().getFirst().key();
        when(similarProductSearchService.search(any(), any(SearchSimilarUserProductsCommand.class)))
                .thenReturn(searchResult("similar shoes in the same size", similarProducts));
        stubCart(firstOfferKey, SHOES_USER_ID);
        scriptModel(
                tool("b-inventory", "search_inventory", "{\"query\":\"mesh\",\"limit\":1}"),
                tool("b-similar", "find_similar_products",
                        "{\"inventoryItemId\":\"" + INVENTORY_ITEM_ID
                                + "\",\"query\":\"similar shoes in the same size\"}"),
                text("These options are anchored to your owned pair."),
                tool("b-cart", "prepare_carts",
                        "{\"offers\":[{\"offerKey\":\"" + firstOfferKey + "\",\"quantity\":1}]}"),
                text("The first option in your size is in your cart.")
        );

        UUID conversationId = conversationService.create(new CreateAgentConversationCommand(
                SHOES_USER_ID, "North-star similar shoes")).conversationId();
        UUID similarityRunId = runTurn(
                SHOES_USER_ID,
                conversationId,
                "Find shoes similar to the ones I already have.",
                "north-star-b-similar"
        );
        UUID cartRunId = runTurn(
                SHOES_USER_ID,
                conversationId,
                "Buy the first one in the same size.",
                "north-star-b-cart"
        );

        assertThat(invocations(similarityRunId))
                .extracting(invocation -> invocation.getToolName(), invocation -> invocation.getStatus())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "search_inventory", AgentToolInvocationStatus.COMPLETED),
                        org.assertj.core.groups.Tuple.tuple(
                                "find_similar_products", AgentToolInvocationStatus.COMPLETED)
                );
        assertThat(artifactRepository.findByRunIdOrderByCreatedAtAscOrdinalAsc(similarityRunId))
                .anySatisfy(artifact -> {
                    assertThat(artifact.getArtifactType()).isEqualTo(AgentArtifactType.INVENTORY_ITEM);
                    assertThat(artifact.getInventoryItemId()).isEqualTo(INVENTORY_ITEM_ID);
                })
                .filteredOn(artifact -> artifact.getArtifactType() == AgentArtifactType.PRODUCT)
                .extracting(artifact -> artifact.getOfferKey())
                .containsExactly(firstOfferKey, secondOfferKey);

        ArgumentCaptor<RehydrateUserInventoryProductQuery> rehydrate =
                ArgumentCaptor.forClass(RehydrateUserInventoryProductQuery.class);
        verify(inventoryRehydrationService).rehydrate(rehydrate.capture());
        assertThat(rehydrate.getValue().userId()).isEqualTo(SHOES_USER_ID);
        assertThat(rehydrate.getValue().inventoryItemId()).isEqualTo(INVENTORY_ITEM_ID);
        assertThat(rehydrate.getValue().countryCode()).isEqualTo("US");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CanonicalProduct>> anchoredProducts = ArgumentCaptor.forClass(List.class);
        verify(productReferencePersistenceService).replace(
                org.mockito.ArgumentMatchers.eq(SHOES_USER_ID), anchoredProducts.capture());
        CanonicalProduct anchor = anchoredProducts.getValue().getFirst();
        assertThat(anchor.key()).isEqualTo(commerceReference.canonicalProductKey());
        assertThat(anchor.attributes())
                .singleElement()
                .satisfies(option -> {
                    assertThat(option.name()).isEqualTo("Size");
                    assertThat(option.value()).isEqualTo("42");
                });
        assertThat(anchor.offers().getFirst().selectedOptions())
                .singleElement()
                .satisfies(option -> assertThat(option.value()).isEqualTo("42"));

        ArgumentCaptor<SearchSimilarUserProductsCommand> similar =
                ArgumentCaptor.forClass(SearchSimilarUserProductsCommand.class);
        verify(similarProductSearchService).search(any(), similar.capture());
        assertThat(similar.getValue().userId()).isEqualTo(SHOES_USER_ID);
        assertThat(similar.getValue().canonicalProductKey()).isEqualTo(commerceReference.canonicalProductKey());
        assertThat(similar.getValue().query()).isEqualTo("similar shoes in the same size");

        assertThat(invocations(cartRunId))
                .singleElement()
                .satisfies(invocation -> {
                    assertThat(invocation.getToolName()).isEqualTo("prepare_carts");
                    assertThat(invocation.getStatus()).isEqualTo(AgentToolInvocationStatus.COMPLETED);
                    assertThat(invocation.getArgumentsJson()).contains(firstOfferKey).doesNotContain(secondOfferKey);
                });
        ArgumentCaptor<PartitionSelectedOffersQuery> partition =
                ArgumentCaptor.forClass(PartitionSelectedOffersQuery.class);
        verify(cartService).partitionSelectedOffers(partition.capture());
        assertThat(partition.getValue().items())
                .singleElement()
                .extracting(PartitionSelectedOffersQuery.Item::offerKey)
                .isEqualTo(firstOfferKey);
    }

    private UUID runTurn(UUID userId, UUID conversationId, String message, String clientTurnId) throws Exception {
        return runTurn(userId, conversationId, message, clientTurnId, null);
    }

    private UUID runTurn(
            UUID userId,
            UUID conversationId,
            String message,
            String clientTurnId,
            VisibleProductContextCommand visibleProductContext
    ) throws Exception {
        return runTurnExpecting(
                userId,
                conversationId,
                message,
                clientTurnId,
                visibleProductContext,
                AgentRunStatus.COMPLETED
        );
    }

    private UUID runTurnExpecting(
            UUID userId,
            UUID conversationId,
            String message,
            String clientTurnId,
            VisibleProductContextCommand visibleProductContext,
            AgentRunStatus expectedStatus
    ) throws Exception {
        var accepted = turnService.submit(new SubmitAgentTurnCommand(
                userId, conversationId, message, clientTurnId, visibleProductContext, null));
        coordinator.schedule(accepted.runId());
        awaitTerminalRun(accepted.runId());
        assertThat(runRepository.findById(accepted.runId()).orElseThrow().getStatus())
                .isEqualTo(expectedStatus);
        return accepted.runId();
    }

    private void awaitTerminalRun(UUID runId) throws Exception {
        for (int attempt = 0; attempt < 200; attempt++) {
            if (runRepository.findById(runId).map(run -> run.getStatus().terminal()).orElse(false)) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Agent run did not reach a terminal state: " + runId);
    }

    private List<com.meant.api.module.agent.entity.AgentToolInvocation> invocations(UUID runId) {
        return invocationRepository.findByRunIdOrderByCreatedAtAsc(runId);
    }

    private void scriptModel(AgentModelResponse... responses) {
        Queue<AgentModelResponse> scripted = new ArrayDeque<>(List.of(responses));
        when(modelGateway.turn(any(), any(), any())).thenAnswer(invocation -> {
            AgentModelResponse response = scripted.poll();
            if (response == null) {
                throw new AssertionError("The agent requested more model turns than the scenario supplied");
            }
            return response;
        });
    }

    private AgentModelResponse tool(String id, String name, String argumentsJson) {
        return new AgentModelResponse(
                "",
                List.of(new AgentModelToolCall(id, name, argumentsJson)),
                new AgentModelUsage(20L, 10L),
                "tool_calls",
                "scripted-north-star"
        );
    }

    private AgentModelResponse text(String text) {
        return new AgentModelResponse(
                text,
                List.of(),
                new AgentModelUsage(20L, 10L),
                "stop",
                "scripted-north-star"
        );
    }

    private void stubCart(String expectedOfferKey, UUID expectedUserId) {
        when(cartService.partitionSelectedOffers(any(PartitionSelectedOffersQuery.class)))
                .thenAnswer(invocation -> {
                    PartitionSelectedOffersQuery query = invocation.getArgument(0);
                    assertThat(query.userId()).isEqualTo(expectedUserId);
                    assertThat(query.items()).singleElement()
                            .extracting(PartitionSelectedOffersQuery.Item::offerKey)
                            .isEqualTo(expectedOfferKey);
                    return List.of(new CartOfferPartitionResult(
                            "SHOPIFY:merchant-north-star",
                            "SHOPIFY",
                            null,
                            "merchant-north-star",
                            null,
                            "north-star.example.test",
                            List.of(new CartOfferPartitionResult.Item(expectedOfferKey, 1))
                    ));
                });
        when(cartService.create(any(CreateCartCommand.class), any(UUID.class)))
                .thenAnswer(invocation -> cartResult(
                        UUID.randomUUID(), expectedOfferKey, invocation.<CreateCartCommand>getArgument(0).userId()));
    }

    private CartResult cartResult(UUID cartId, String offerKey, UUID userId) {
        Instant now = Instant.now();
        CartLineResult line = new CartLineResult(
                UUID.randomUUID(),
                "remote-line-1",
                "product-1",
                "North-star product",
                "North Star",
                "variant-1",
                "Selected size",
                1,
                "100.00",
                "100.00",
                "USD",
                offerKey,
                "product-1",
                "[{\"name\":\"Size\",\"value\":\"Selected size\"}]",
                "[]",
                null,
                "SHOPIFY",
                null,
                "merchant-north-star",
                now,
                now
        );
        return new CartResult(
                cartId,
                null,
                "north-star.example.test",
                "SHOPIFY",
                null,
                "merchant-north-star",
                "SHOPIFY:merchant-north-star",
                "https://north-star.example.test/ucp",
                "remote-cart-" + userId,
                null,
                null,
                null,
                1,
                "100.00",
                "100.00",
                "USD",
                true,
                now,
                now,
                now.plusSeconds(3600),
                now,
                now,
                now,
                List.of(),
                List.of(line),
                List.of(),
                List.of()
        );
    }

    private UserGroupedProductSearchResult searchResult(String query, List<CanonicalProduct> products) {
        return new UserGroupedProductSearchResult(
                query,
                query,
                "north-star-profile",
                false,
                0,
                products.size(),
                null,
                false,
                false,
                products,
                0,
                false,
                List.of()
        );
    }

    private CanonicalProduct product(String id, String title, String size) {
        Instant now = Instant.now();
        ExternalIdentifier merchant = identifier(ExternalIdentifierType.MERCHANT, "merchant-north-star");
        ExternalIdentifier product = identifier(ExternalIdentifierType.PRODUCT, "product-" + id);
        ExternalIdentifier variant = identifier(ExternalIdentifierType.VARIANT, "variant-" + id + "-" + size);
        DiscoverySourceIdentity discovery = new DiscoverySourceIdentity(
                PROVIDER, ResultSourceType.PROVIDER_CATALOG, "GLOBAL_CATALOG");
        ResultProvenance provenance = new ResultProvenance(
                PROVIDER,
                discovery,
                null,
                merchant,
                "north-star.example.test",
                product,
                variant,
                new ResultFreshness(now, now.plusSeconds(300)),
                new ResultSourceReference(ResultSourceType.PROVIDER_CATALOG, "GLOBAL_CATALOG", null)
        );
        List<ProductAttribute> selectedOptions = List.of(new ProductAttribute("variant", "Size", size));
        Offer offer = new Offer(
                new OfferIdentity(
                        PROVIDER,
                        OfferMerchantScope.external(merchant),
                        product,
                        variant,
                        selectedOptions,
                        List.of(),
                        null
                ),
                "North-star merchant",
                "Size " + size,
                new Money(10_000, "USD"),
                null,
                new OfferAvailability(OfferAvailabilityStatus.IN_STOCK, 5, null),
                List.of(),
                null,
                List.of(provenance)
        );
        return new CanonicalProduct(
                "canonical:" + id,
                title,
                "A grounded north-star product.",
                List.of(),
                selectedOptions,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(provenance),
                List.of(offer)
        );
    }

    private UserInventoryItemResult inventoryItem(UserInventoryCommerceReference commerceReference) {
        Instant now = Instant.now();
        return new UserInventoryItemResult(
                INVENTORY_ITEM_ID,
                UserInventorySource.MEANT_PURCHASE,
                "owned-trail-shoe",
                "owned-trail-shoe-hash",
                "Owned trail shoes",
                "North-star shoes",
                UserInventoryCategory.APPAREL,
                "Trail shoes in size 42",
                null,
                null,
                null,
                SHOES_USER_ID + "/owned-trail-shoes.jpg",
                1,
                "pair",
                "closet",
                null,
                "42",
                "Blue",
                "Mesh",
                List.of("shoes", "size 42"),
                false,
                false,
                null,
                now.minusSeconds(3600),
                null,
                commerceReference,
                UUID.randomUUID(),
                now,
                now
        );
    }

    private ExternalIdentifier identifier(ExternalIdentifierType type, String value) {
        return new ExternalIdentifier(type, PROVIDER.value(), value);
    }

    private void persistUser(UUID userId, String email) {
        Instant now = Instant.now();
        userRepository.saveAndFlush(User.builder()
                .id(userId)
                .email(email)
                .firstName("North")
                .surname("Star")
                .createdAt(now)
                .updatedAt(now)
                .build());
    }
}
