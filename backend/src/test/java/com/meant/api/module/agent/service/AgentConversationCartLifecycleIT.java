package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.PostgresIntegrationTestSupport;
import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.constant.AgentModelRole;
import com.meant.api.module.agent.constant.AgentRunStatus;
import com.meant.api.module.agent.constant.AgentToolInvocationStatus;
import com.meant.api.module.agent.repository.AgentArtifactReferenceRepository;
import com.meant.api.module.agent.repository.AgentRunRepository;
import com.meant.api.module.agent.repository.AgentToolInvocationRepository;
import com.meant.api.module.agent.service.command.CreateAgentConversationCommand;
import com.meant.api.module.agent.service.command.SubmitAgentTurnCommand;
import com.meant.api.module.agent.service.dto.AgentModelRequest;
import com.meant.api.module.agent.service.dto.AgentModelResponse;
import com.meant.api.module.agent.service.dto.AgentModelToolCall;
import com.meant.api.module.agent.service.dto.AgentModelUsage;
import com.meant.api.module.agent.service.port.AgentModelGateway;
import com.meant.api.module.cart.service.CartService;
import com.meant.api.module.cart.service.command.CreateCartCommand;
import com.meant.api.module.cart.service.command.UpdateCartCommand;
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
import com.meant.api.module.user.entity.User;
import com.meant.api.module.user.repository.UserRepository;
import com.meant.api.module.user.service.UserGroupedProductSearchService;
import com.meant.api.module.user.service.command.SearchUserProductsCommand;
import com.meant.api.module.user.service.dto.UserGroupedProductSearchResult;
import java.time.Instant;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
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
class AgentConversationCartLifecycleIT extends PostgresIntegrationTestSupport {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000951");
    private static final UUID CART_ID = UUID.fromString("00000000-0000-0000-0000-000000000952");
    private static final UUID ORIGINAL_LINE_ID = UUID.fromString("00000000-0000-0000-0000-000000000953");
    private static final UUID READDED_LINE_ID = UUID.fromString("00000000-0000-0000-0000-000000000954");
    private static final ProviderIdentity PROVIDER = new ProviderIdentity("SHOPIFY");
    private static final String ROUTING_SCOPE = "SHOPIFY:merchant-jackets";

    @Autowired private UserRepository userRepository;
    @Autowired private AgentConversationService conversationService;
    @Autowired private AgentTurnService turnService;
    @Autowired private AgentRunCoordinator coordinator;
    @Autowired private AgentRunRepository runRepository;
    @Autowired private AgentToolInvocationRepository invocationRepository;
    @Autowired private AgentArtifactReferenceRepository artifactRepository;

    @MockitoBean private AgentModelGateway modelGateway;
    @MockitoBean private UserGroupedProductSearchService catalogSearchService;
    @MockitoBean private CartService cartService;

    @Test
    void persistedConversationResolvesSecondJacketThenRemoveItAndAddItAgain() throws Exception {
        persistUser();
        List<CanonicalProduct> jackets = List.of(
                product("rain-shell", "Lightweight rain jacket", "Blue"),
                product("cotton-field", "Survival cotton jacket", "Black")
        );
        String firstOfferKey = jackets.getFirst().offers().getFirst().key();
        String secondOfferKey = jackets.get(1).offers().getFirst().key();
        when(catalogSearchService.search(any(), any(SearchUserProductsCommand.class)))
                .thenReturn(searchResult(jackets));

        when(cartService.partitionSelectedOffers(any(PartitionSelectedOffersQuery.class)))
                .thenReturn(List.of(new CartOfferPartitionResult(
                        ROUTING_SCOPE,
                        "SHOPIFY",
                        null,
                        "merchant-jackets",
                        null,
                        "jackets.example.test",
                        List.of(new CartOfferPartitionResult.Item(secondOfferKey, 1))
                )));
        when(cartService.create(any(CreateCartCommand.class), any(UUID.class)))
                .thenReturn(cartResult(List.of(line(ORIGINAL_LINE_ID, "remote-line-original", secondOfferKey))));
        List<UpdateCartCommand> updateCommands = new CopyOnWriteArrayList<>();
        AtomicInteger updateNumber = new AtomicInteger();
        when(cartService.update(any(UpdateCartCommand.class), any(UUID.class))).thenAnswer(invocation -> {
            UpdateCartCommand command = invocation.getArgument(0);
            updateCommands.add(command);
            return switch (updateNumber.getAndIncrement()) {
                case 0 -> cartResult(List.of());
                case 1 -> cartResult(List.of(line(READDED_LINE_ID, "remote-line-readded", secondOfferKey)));
                default -> throw new AssertionError("The lifecycle issued an unexpected extra cart update");
            };
        });

        List<AgentModelRequest> modelRequests = new CopyOnWriteArrayList<>();
        scriptModel(modelRequests,
                tool("search-jackets", "search_catalog", "{\"query\":\"jackets\",\"limit\":2}"),
                text("I found two jackets for you."),
                tool("prepare-second", "prepare_carts",
                        "{\"offers\":[{\"offerKey\":\"" + secondOfferKey + "\",\"quantity\":1}]}"),
                text("I put the second jacket into your cart."),
                tool("remove-it", "remove_cart_line",
                        "{\"cartId\":\"" + CART_ID + "\",\"cartLineId\":\"" + ORIGINAL_LINE_ID + "\"}"),
                text("I removed that jacket from your cart."),
                tool("add-again", "add_cart_line",
                        "{\"cartId\":\"" + CART_ID + "\",\"offerKey\":\"" + secondOfferKey
                                + "\",\"quantity\":1}"),
                text("I added the jacket back to your cart."));

        UUID conversationId = conversationService.create(
                new CreateAgentConversationCommand(USER_ID, "Jacket cart lifecycle")).conversationId();
        UUID searchRunId = runTurn(conversationId, "Find me two jackets.", "cart-lifecycle-search");
        UUID prepareRunId = runTurn(
                conversationId, "Put the second jacket into the cart.", "cart-lifecycle-prepare");
        UUID removeRunId = runTurn(conversationId, "Remove it from the cart.", "cart-lifecycle-remove");
        UUID readdRunId = runTurn(conversationId, "Add it again.", "cart-lifecycle-readd");

        assertThat(invocations(searchRunId))
                .singleElement()
                .satisfies(invocation -> {
                    assertThat(invocation.getToolName()).isEqualTo("search_catalog");
                    assertThat(invocation.getStatus()).isEqualTo(AgentToolInvocationStatus.COMPLETED);
                    assertThat(invocation.getArgumentsJson()).isEqualTo("{\"limit\":2,\"query\":\"jackets\"}");
                });
        assertThat(artifactRepository.findByRunIdOrderByCreatedAtAscOrdinalAsc(searchRunId))
                .filteredOn(artifact -> artifact.getArtifactType() == AgentArtifactType.PRODUCT)
                .extracting(artifact -> artifact.getOfferKey())
                .containsExactly(firstOfferKey, secondOfferKey);
        ArgumentCaptor<SearchUserProductsCommand> search = ArgumentCaptor.forClass(SearchUserProductsCommand.class);
        verify(catalogSearchService).search(any(), search.capture());
        assertThat(search.getValue().query()).isEqualTo("jackets");
        assertThat(search.getValue().limit()).isEqualTo(2);

        ArgumentCaptor<PartitionSelectedOffersQuery> partition =
                ArgumentCaptor.forClass(PartitionSelectedOffersQuery.class);
        verify(cartService).partitionSelectedOffers(partition.capture());
        assertThat(partition.getValue().userId()).isEqualTo(USER_ID);
        assertThat(partition.getValue().items())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.offerKey()).isEqualTo(secondOfferKey);
                    assertThat(item.quantity()).isEqualTo(1);
                });

        assertCompletedMutation(prepareRunId, "prepare_carts", CART_ID, secondOfferKey, ORIGINAL_LINE_ID);
        assertCompletedMutation(removeRunId, "remove_cart_line", CART_ID, secondOfferKey, null);
        assertCompletedMutation(readdRunId, "add_cart_line", CART_ID, secondOfferKey, READDED_LINE_ID);

        ArgumentCaptor<CreateCartCommand> create = ArgumentCaptor.forClass(CreateCartCommand.class);
        verify(cartService).create(create.capture(), any(UUID.class));
        assertThat(create.getValue().userId()).isEqualTo(USER_ID);
        assertThat(create.getValue().addItems())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.offerKey()).isEqualTo(secondOfferKey);
                    assertThat(item.quantity()).isEqualTo(1);
                });
        assertThat(updateCommands).hasSize(2);
        assertThat(updateCommands.getFirst()).satisfies(command -> {
            assertThat(command.cartId()).isEqualTo(CART_ID);
            assertThat(command.userId()).isEqualTo(USER_ID);
            assertThat(command.addItems()).isEmpty();
            assertThat(command.removeCartLineIds()).containsExactly(ORIGINAL_LINE_ID);
        });
        assertThat(updateCommands.get(1)).satisfies(command -> {
            assertThat(command.cartId()).isEqualTo(CART_ID);
            assertThat(command.userId()).isEqualTo(USER_ID);
            assertThat(command.removeCartLineIds()).isEmpty();
            assertThat(command.addItems())
                    .singleElement()
                    .satisfies(item -> {
                        assertThat(item.offerKey()).isEqualTo(secondOfferKey);
                        assertThat(item.quantity()).isEqualTo(1);
                    });
        });

        AgentModelRequest readdRequest = modelRequests.stream()
                .filter(request -> request.messages().stream()
                        .anyMatch(message -> "Add it again.".equals(message.text())))
                .findFirst()
                .orElseThrow();
        assertThat(readdRequest.messages())
                .filteredOn(message -> message.role() == AgentModelRole.USER)
                .extracting(message -> message.text())
                .contains(
                        "Find me two jackets.",
                        "Put the second jacket into the cart.",
                        "Remove it from the cart.",
                        "Add it again."
                );
        assertThat(readdRequest.messages())
                .filteredOn(message -> message.role() == AgentModelRole.ASSISTANT)
                .extracting(message -> message.text())
                .contains(
                        "I found two jackets for you.",
                        "I put the second jacket into your cart.",
                        "I removed that jacket from your cart."
                );
        assertThat(readdRequest.messages())
                .filteredOn(message -> message.role() == AgentModelRole.USER)
                .extracting(message -> message.text())
                .anySatisfy(context -> assertThat(context)
                        .contains("Authoritative current commerce state:")
                        .contains("cartId=" + CART_ID)
                        .contains("lines=none")
                        .contains("Most recently removed re-add reference")
                        .contains("priorCartLineId=" + ORIGINAL_LINE_ID)
                        .contains("offerKey=" + secondOfferKey));
    }

    private void assertCompletedMutation(
            UUID runId,
            String toolName,
            UUID expectedCartId,
            String expectedOfferKey,
            UUID expectedCurrentLineId
    ) {
        assertThat(invocations(runId))
                .singleElement()
                .satisfies(invocation -> {
                    assertThat(invocation.getToolName()).isEqualTo(toolName);
                    assertThat(invocation.getStatus()).isEqualTo(AgentToolInvocationStatus.COMPLETED);
                    switch (toolName) {
                        case "prepare_carts" -> assertThat(invocation.getArgumentsJson()).isEqualTo(
                                "{\"offers\":[{\"offerKey\":\"" + expectedOfferKey + "\",\"quantity\":1}]}");
                        case "remove_cart_line" -> assertThat(invocation.getArgumentsJson()).isEqualTo(
                                "{\"cartId\":\"" + expectedCartId + "\",\"cartLineId\":\""
                                        + ORIGINAL_LINE_ID + "\"}");
                        case "add_cart_line" -> assertThat(invocation.getArgumentsJson()).isEqualTo(
                                "{\"cartId\":\"" + expectedCartId + "\",\"offerKey\":\"" + expectedOfferKey
                                        + "\",\"quantity\":1}");
                        default -> throw new AssertionError("Unexpected lifecycle mutation: " + toolName);
                    }
                });
        var artifacts = artifactRepository.findByRunIdOrderByCreatedAtAscOrdinalAsc(runId);
        assertThat(artifacts)
                .filteredOn(artifact -> artifact.getArtifactType() == AgentArtifactType.CART)
                .singleElement()
                .satisfies(artifact -> {
                    assertThat(artifact.getCartId()).isEqualTo(expectedCartId);
                    assertThat(artifact.getPayloadJson()).contains("\"cartId\":\"" + expectedCartId + "\"");
                });
        if (expectedCurrentLineId == null) {
            assertThat(artifacts)
                    .filteredOn(artifact -> artifact.getArtifactType() == AgentArtifactType.CART_LINE)
                    .isEmpty();
            return;
        }
        assertThat(artifacts)
                .filteredOn(artifact -> artifact.getArtifactType() == AgentArtifactType.CART_LINE)
                .singleElement()
                .satisfies(artifact -> {
                    assertThat(artifact.getCartId()).isEqualTo(expectedCartId);
                    assertThat(artifact.getCartLineId()).isEqualTo(expectedCurrentLineId);
                    assertThat(artifact.getOfferKey()).isEqualTo(expectedOfferKey);
                });
    }

    private UUID runTurn(UUID conversationId, String message, String clientTurnId) throws Exception {
        var accepted = turnService.submit(new SubmitAgentTurnCommand(USER_ID, conversationId, message, clientTurnId));
        coordinator.schedule(accepted.runId());
        awaitTerminalRun(accepted.runId());
        assertThat(runRepository.findById(accepted.runId()).orElseThrow().getStatus())
                .isEqualTo(AgentRunStatus.COMPLETED);
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

    private void scriptModel(List<AgentModelRequest> requests, AgentModelResponse... responses) {
        Queue<AgentModelResponse> scripted = new ConcurrentLinkedQueue<>(List.of(responses));
        when(modelGateway.turn(any(), any(), any())).thenAnswer(invocation -> {
            requests.add(invocation.getArgument(0));
            AgentModelResponse response = scripted.poll();
            if (response == null) {
                throw new AssertionError("The agent requested more model turns than the lifecycle supplied");
            }
            return response;
        });
    }

    private AgentModelResponse tool(String id, String name, String argumentsJson) {
        return new AgentModelResponse(
                "",
                List.of(new AgentModelToolCall(id, name, argumentsJson)),
                new AgentModelUsage(30L, 10L),
                "tool_calls",
                "scripted-cart-lifecycle"
        );
    }

    private AgentModelResponse text(String value) {
        return new AgentModelResponse(
                value,
                List.of(),
                new AgentModelUsage(30L, 10L),
                "stop",
                "scripted-cart-lifecycle"
        );
    }

    private CartResult cartResult(List<CartLineResult> lines) {
        Instant now = Instant.now();
        String total = lines.isEmpty() ? "0.00" : "89.95";
        return new CartResult(
                CART_ID,
                null,
                "jackets.example.test",
                "SHOPIFY",
                null,
                "merchant-jackets",
                ROUTING_SCOPE,
                "https://jackets.example.test/ucp",
                "remote-cart-jackets",
                null,
                null,
                null,
                lines.size(),
                total,
                total,
                "USD",
                true,
                now,
                now,
                now.plusSeconds(3600),
                now,
                now,
                now,
                List.of(),
                lines,
                List.of(),
                List.of()
        );
    }

    private CartLineResult line(UUID lineId, String remoteLineId, String offerKey) {
        Instant now = Instant.now();
        return new CartLineResult(
                lineId,
                remoteLineId,
                "product-cotton-field",
                "Survival cotton jacket",
                "variant-cotton-field-Black",
                "Black",
                1,
                "89.95",
                "89.95",
                "USD",
                offerKey,
                "SHOPIFY",
                null,
                "merchant-jackets",
                now,
                now
        );
    }

    private UserGroupedProductSearchResult searchResult(List<CanonicalProduct> products) {
        return new UserGroupedProductSearchResult(
                "jackets",
                "jackets",
                "cart-lifecycle-profile",
                false,
                0,
                2,
                null,
                false,
                false,
                products,
                0,
                false,
                List.of()
        );
    }

    private CanonicalProduct product(String id, String title, String color) {
        Instant now = Instant.now();
        ExternalIdentifier merchant = identifier(ExternalIdentifierType.MERCHANT, "merchant-jackets");
        ExternalIdentifier product = identifier(ExternalIdentifierType.PRODUCT, "product-" + id);
        ExternalIdentifier variant = identifier(ExternalIdentifierType.VARIANT, "variant-" + id + "-" + color);
        DiscoverySourceIdentity discovery = new DiscoverySourceIdentity(
                PROVIDER, ResultSourceType.PROVIDER_CATALOG, "GLOBAL_CATALOG");
        ResultProvenance provenance = new ResultProvenance(
                PROVIDER,
                discovery,
                null,
                merchant,
                "jackets.example.test",
                product,
                variant,
                new ResultFreshness(now, now.plusSeconds(300)),
                new ResultSourceReference(ResultSourceType.PROVIDER_CATALOG, "GLOBAL_CATALOG", null)
        );
        List<ProductAttribute> selectedOptions = List.of(new ProductAttribute("variant", "Color", color));
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
                "Jacket merchant",
                color,
                new Money(8_995, "USD"),
                null,
                new OfferAvailability(OfferAvailabilityStatus.IN_STOCK, 5, null),
                List.of(),
                null,
                List.of(provenance)
        );
        return new CanonicalProduct(
                "canonical:" + id,
                title,
                "A grounded jacket.",
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

    private ExternalIdentifier identifier(ExternalIdentifierType type, String value) {
        return new ExternalIdentifier(type, PROVIDER.value(), value);
    }

    private void persistUser() {
        Instant now = Instant.now();
        userRepository.saveAndFlush(User.builder()
                .id(USER_ID)
                .email("cart-lifecycle@example.test")
                .firstName("Cart")
                .surname("Lifecycle")
                .createdAt(now)
                .updatedAt(now)
                .build());
    }
}
