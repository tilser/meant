package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.PostgresIntegrationTestSupport;
import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.constant.AgentRunStatus;
import com.meant.api.module.agent.constant.AgentToolInvocationStatus;
import com.meant.api.module.agent.constant.ShoppingMissionStatus;
import com.meant.api.module.agent.repository.AgentArtifactReferenceRepository;
import com.meant.api.module.agent.repository.AgentRunRepository;
import com.meant.api.module.agent.repository.AgentToolInvocationRepository;
import com.meant.api.module.agent.repository.ShoppingMissionRepository;
import com.meant.api.module.agent.service.command.CreateAgentConversationCommand;
import com.meant.api.module.agent.service.command.RecordAgentUserActionCommand;
import com.meant.api.module.agent.service.command.SubmitAgentTurnCommand;
import com.meant.api.module.agent.service.dto.AgentArtifact;
import com.meant.api.module.agent.service.dto.AgentModelRequest;
import com.meant.api.module.agent.service.dto.AgentModelResponse;
import com.meant.api.module.agent.service.dto.AgentModelToolCall;
import com.meant.api.module.agent.service.dto.AgentModelUsage;
import com.meant.api.module.agent.service.port.AgentModelGateway;
import com.meant.api.module.cart.constant.CheckoutNextAction;
import com.meant.api.module.cart.service.CartService;
import com.meant.api.module.cart.service.command.CreateCartCommand;
import com.meant.api.module.cart.service.dto.CartOfferPartitionResult;
import com.meant.api.module.cart.service.dto.CartResult;
import com.meant.api.module.cart.service.dto.CheckoutResult;
import com.meant.api.module.catalog.service.dto.Money;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.OfferAvailability;
import com.meant.api.module.catalog.service.dto.OfferAvailabilityStatus;
import com.meant.api.module.catalog.service.dto.RehydratedProductDetails;
import com.meant.api.module.merchant.constant.CommerceExecutionRail;
import com.meant.api.module.merchant.service.dto.MerchantExecutionPolicy;
import com.meant.api.module.user.entity.User;
import com.meant.api.module.user.repository.UserRepository;
import com.meant.api.module.user.service.UserProductVariantSelectionService;
import com.meant.api.module.user.service.command.SelectUserProductVariantCommand;
import com.meant.api.module.user.service.dto.UserProductVariantSelectionResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
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
class AgentNorthStarMissionCheckoutIT extends PostgresIntegrationTestSupport {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000911");
    private static final UUID CART_ONE_ID = UUID.fromString("00000000-0000-0000-0000-000000000921");
    private static final UUID CART_TWO_ID = UUID.fromString("00000000-0000-0000-0000-000000000922");
    private static final UUID CHECKOUT_ONE_ID = UUID.fromString("00000000-0000-0000-0000-000000000931");
    private static final UUID CHECKOUT_TWO_ID = UUID.fromString("00000000-0000-0000-0000-000000000932");
    private static final String BLANKET_PRODUCT = "canonical:picnic:blanket";
    private static final String CUPS_PRODUCT = "canonical:picnic:cups";
    private static final String SNACKS_PRODUCT = "canonical:picnic:snacks";
    private static final String BLANKET_OFFER = "offer:picnic:blanket";
    private static final String CUPS_OFFER = "offer:picnic:cups";
    private static final String SNACKS_OFFER = "offer:picnic:snacks";

    @Autowired private UserRepository userRepository;
    @Autowired private AgentConversationService conversationService;
    @Autowired private AgentTurnService turnService;
    @Autowired private AgentRunCoordinator coordinator;
    @Autowired private AgentUserActionService userActionService;
    @Autowired private AgentArtifactService artifactService;
    @Autowired private AgentRunRepository runRepository;
    @Autowired private AgentToolInvocationRepository invocationRepository;
    @Autowired private AgentArtifactReferenceRepository artifactRepository;
    @Autowired private ShoppingMissionRepository missionRepository;

    @MockitoBean private AgentModelGateway modelGateway;
    @MockitoBean private UserProductVariantSelectionService variantSelectionService;
    @MockitoBean private CartService cartService;

    @BeforeEach
    void persistUserAndConfigureCommerceBoundary() {
        Instant now = Instant.now();
        userRepository.saveAndFlush(User.builder()
                .id(USER_ID)
                .email("mission-eval@example.test")
                .firstName("Mission")
                .surname("Evaluator")
                .createdAt(now)
                .updatedAt(now)
                .build());

        when(cartService.partitionSelectedOffers(any())).thenReturn(List.of(
                partition("shopify:blanket", "blankets.example", BLANKET_OFFER),
                partition("shopify:cups", "cups.example", CUPS_OFFER),
                partition("shopify:snacks", "snacks.example", SNACKS_OFFER)
        ));
        when(cartService.create(any(), any())).thenAnswer(invocation -> {
            CreateCartCommand command = invocation.getArgument(0);
            return switch (command.addItems().getFirst().offerKey()) {
                case BLANKET_OFFER -> cart(CART_ONE_ID, "blankets.example", "shopify:blanket", 4999L);
                case CUPS_OFFER -> cart(CART_TWO_ID, "cups.example", "shopify:cups", 1899L);
                default -> throw new IllegalStateException("simulated merchant cart outage");
            };
        });
        when(cartService.checkout(any(), any())).thenAnswer(invocation -> {
            com.meant.api.module.cart.service.query.GetCheckoutQuery query = invocation.getArgument(0);
            if (query.cartId().equals(CART_ONE_ID)) {
                return checkout(CART_ONE_ID, CHECKOUT_ONE_ID, "blankets.example", 4999L);
            }
            return checkout(CART_TWO_ID, CHECKOUT_TWO_ID, "cups.example", 1899L);
        });
    }

    @Test
    void picnicMissionBuildsCartsAndRequiresUserApprovalBeforeCheckout() throws InterruptedException {
        var conversation = conversationService.create(
                new CreateAgentConversationCommand(USER_ID, "Summer picnic mission"));
        artifactService.persist(
                conversation.conversationId(),
                null,
                null,
                null,
                List.of(
                        product(1, BLANKET_PRODUCT, BLANKET_OFFER, "Picnic blanket"),
                        product(2, CUPS_PRODUCT, CUPS_OFFER, "Reusable cups"),
                        product(3, SNACKS_PRODUCT, SNACKS_OFFER, "Picnic snacks")
                )
        );
        stubVariantSelections();

        AtomicInteger modelTurn = new AtomicInteger();
        List<AgentModelRequest> requests = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> {
            AgentModelRequest request = invocation.getArgument(0);
            requests.add(request);
            return switch (modelTurn.getAndIncrement()) {
                case 0 -> toolResponse("create-mission", "create_shopping_mission", missionArguments());
                case 1 -> selectResponse("select-blanket", BLANKET_OFFER);
                case 2 -> selectResponse("select-cups", CUPS_OFFER);
                case 3 -> selectResponse("select-snacks", SNACKS_OFFER);
                case 4 -> toolResponse("prepare-carts", "prepare_carts", """
                        {"offers":[
                          {"offerKey":"%s","quantity":1},
                          {"offerKey":"%s","quantity":8},
                          {"offerKey":"%s","quantity":1}
                        ]}
                        """.formatted(BLANKET_OFFER, CUPS_OFFER, SNACKS_OFFER));
                default -> new AgentModelResponse(
                        "Two merchant carts are ready; confirm before preparing checkout.",
                        List.of(),
                        new AgentModelUsage(80L, 20L),
                        "stop",
                        "scripted-mission-eval"
                );
            };
        }).when(modelGateway).turn(any(), any(), any());

        var accepted = turnService.submit(new SubmitAgentTurnCommand(
                USER_ID,
                conversation.conversationId(),
                "Prepare everything I need for a summer picnic in San Francisco; go ahead through checkout.",
                "north-star-picnic"
        ));
        coordinator.schedule(accepted.runId());

        verify(modelGateway, timeout(8_000).times(6)).turn(any(), any(), any());
        awaitTerminalRun(accepted.runId());
        var checkoutAction = userActionService.perform(new RecordAgentUserActionCommand(
                USER_ID,
                conversation.conversationId(),
                "prepare_checkout",
                "{\"cartIds\":[\"" + CART_ONE_ID + "\",\"" + CART_TWO_ID + "\"]}",
                "north-star-picnic-checkout",
                "Approved checkout preparation"
        ));

        var run = runRepository.findById(accepted.runId()).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(run.getIterationCount()).isEqualTo(6);
        assertThat(run.getToolInvocationCount()).isEqualTo(5);

        var invocations = invocationRepository.findByRunIdOrderByCreatedAtAsc(run.getId());
        assertThat(invocations)
                .extracting(tool -> tool.getToolName())
                .containsExactly(
                        "create_shopping_mission",
                        "select_product_variant",
                        "select_product_variant",
                        "select_product_variant",
                        "prepare_carts"
                );
        assertThat(invocations)
                .extracting(tool -> tool.getStatus())
                .containsOnly(AgentToolInvocationStatus.COMPLETED);
        assertThat(invocations.get(4).getArgumentsJson())
                .contains(BLANKET_OFFER, CUPS_OFFER, SNACKS_OFFER);
        assertThat(invocations.get(4).getResultJson())
                .contains(CART_ONE_ID.toString(), CART_TWO_ID.toString(), "snacks.example")
                .doesNotContain("simulated merchant cart outage");
        assertThat(invocations).noneMatch(tool -> tool.getToolName().equals("complete_checkout"));

        var mission = missionRepository.findFirstByConversationIdAndUserIdOrderByUpdatedAtDesc(
                conversation.conversationId(), USER_ID).orElseThrow();
        assertThat(mission.getStatus()).isEqualTo(ShoppingMissionStatus.CHECKOUT_PREPARED);
        assertThat(mission.getRequirementsJson()).contains("blanket", "cups", "snacks");
        assertThat(mission.getCoverageJson())
                .contains("COVERED", BLANKET_OFFER, CUPS_OFFER, SNACKS_OFFER)
                .doesNotContain("MISSING", "PARTIAL");
        assertThat(mission.getCartReferencesJson()).contains(CART_ONE_ID.toString(), CART_TWO_ID.toString());
        assertThat(mission.getCheckoutReferencesJson())
                .contains(CHECKOUT_ONE_ID.toString(), CHECKOUT_TWO_ID.toString());

        var artifacts = artifactRepository.findByRunIdOrderByCreatedAtAscOrdinalAsc(run.getId());
        assertThat(artifacts).filteredOn(value -> value.getArtifactType() == AgentArtifactType.MISSION)
                .hasSize(1);
        assertThat(artifacts).filteredOn(value -> value.getArtifactType() == AgentArtifactType.CART)
                .extracting(value -> value.getCartId())
                .containsExactlyInAnyOrder(CART_ONE_ID, CART_TWO_ID);
        assertThat(checkoutAction.artifacts()).filteredOn(value -> value.type() == AgentArtifactType.CHECKOUT)
                .satisfiesExactlyInAnyOrder(
                        value -> {
                            assertThat(value.cartId()).isEqualTo(CART_ONE_ID);
                            assertThat(value.checkoutAttemptId()).isEqualTo(CHECKOUT_ONE_ID);
                        },
                        value -> {
                            assertThat(value.cartId()).isEqualTo(CART_TWO_ID);
                            assertThat(value.checkoutAttemptId()).isEqualTo(CHECKOUT_TWO_ID);
                        }
                );
        assertThat(artifacts).allSatisfy(value -> {
            assertThat(value.getMessageId()).isNotNull();
            assertThat(value.getToolInvocationId()).isNotNull();
        });

        ArgumentCaptor<CreateCartCommand> cartCommands = ArgumentCaptor.forClass(CreateCartCommand.class);
        verify(cartService, timeout(2_000).times(3)).create(cartCommands.capture(), any());
        assertThat(cartCommands.getAllValues())
                .allSatisfy(command -> assertThat(command.userId()).isEqualTo(USER_ID))
                .flatExtracting(CreateCartCommand::addItems)
                .extracting(CreateCartCommand.AddItem::offerKey)
                .containsExactlyInAnyOrder(BLANKET_OFFER, CUPS_OFFER, SNACKS_OFFER);
        assertThat(requests).hasSize(6);
        assertThat(requests).allSatisfy(request -> assertThat(request.tools())
                .noneMatch(tool -> tool.name().equals("prepare_checkout")
                        || tool.name().equals("complete_checkout")));
    }

    private AgentModelResponse toolResponse(String id, String name, String arguments) {
        return new AgentModelResponse(
                "",
                List.of(new AgentModelToolCall(id, name, arguments)),
                new AgentModelUsage(50L, 12L),
                "tool_calls",
                "scripted-mission-eval"
        );
    }

    private AgentModelResponse selectResponse(String id, String offerKey) {
        return toolResponse(
                id,
                "select_product_variant",
                "{\"offerKey\":\"" + offerKey + "\",\"selectedOptions\":[]}"
        );
    }

    private void stubVariantSelections() {
        Map<String, Offer> offers = Map.of(
                BLANKET_OFFER, selectedOffer(BLANKET_OFFER, "Picnic blanket"),
                CUPS_OFFER, selectedOffer(CUPS_OFFER, "Reusable cups"),
                SNACKS_OFFER, selectedOffer(SNACKS_OFFER, "Picnic snacks")
        );
        when(variantSelectionService.select(any(), any(SelectUserProductVariantCommand.class)))
                .thenAnswer(invocation -> {
                    SelectUserProductVariantCommand command = invocation.getArgument(1);
                    Offer offer = offers.get(command.anchorOfferKey());
                    if (offer == null) {
                        throw new AssertionError("Unexpected variant selection anchor: " + command.anchorOfferKey());
                    }
                    return new UserProductVariantSelectionResult(selectionDetails(), offer, true);
                });
    }

    private Offer selectedOffer(String offerKey, String label) {
        Offer offer = mock(Offer.class);
        when(offer.key()).thenReturn(offerKey);
        when(offer.merchantName()).thenReturn("Mission merchant");
        when(offer.variantTitle()).thenReturn(label);
        when(offer.price()).thenReturn(new Money(1_000, "USD"));
        when(offer.availability()).thenReturn(new OfferAvailability(
                OfferAvailabilityStatus.IN_STOCK, 10, null));
        when(offer.selectedOptions()).thenReturn(List.of());
        when(offer.provenance()).thenReturn(List.of());
        return offer;
    }

    private RehydratedProductDetails selectionDetails() {
        RehydratedProductDetails details = mock(RehydratedProductDetails.class);
        RehydratedProductDetails.Variant variant = mock(RehydratedProductDetails.Variant.class);
        when(details.options()).thenReturn(List.of());
        when(details.variants()).thenReturn(List.of(variant));
        when(details.selected()).thenReturn(List.of());
        when(details.selectedVariant()).thenReturn(variant);
        when(details.totalVariants()).thenReturn(1);
        when(variant.available()).thenReturn(true);
        when(variant.selectedOptions()).thenReturn(List.of());
        return details;
    }

    private String missionArguments() {
        return """
                {
                  "goal":"Prepare a complete summer picnic for eight people in San Francisco",
                  "assumptions":[
                    {"key":"party-size","value":"Eight people"},
                    {"key":"dietary","value":"Choose broadly shareable snacks pending stricter preferences"}
                  ],
                  "requirements":[
                    {"id":"blanket","label":"Picnic blanket","requiredQuantity":1,"optional":false,"searchTerms":["picnic blanket"]},
                    {"id":"cups","label":"Reusable cups","requiredQuantity":1,"optional":false,"searchTerms":["reusable cups"]},
                    {"id":"snacks","label":"Shareable snacks","requiredQuantity":1,"optional":false,"searchTerms":["picnic snacks"]}
                  ],
                  "constraints":{"partySize":8,"occasion":"summer picnic","dietaryRequirements":[],"budget":{"amountMinor":20000,"currency":"USD"}},
                  "alternatives":[
                    {"requirementId":"blanket","canonicalProductKey":"%s","offerKey":"%s","label":"Picnic blanket","selected":true},
                    {"requirementId":"cups","canonicalProductKey":"%s","offerKey":"%s","label":"Reusable cups","selected":true},
                    {"requirementId":"snacks","canonicalProductKey":"%s","offerKey":"%s","label":"Picnic snacks","selected":true}
                  ]
                }
                """.formatted(
                BLANKET_PRODUCT, BLANKET_OFFER,
                CUPS_PRODUCT, CUPS_OFFER,
                SNACKS_PRODUCT, SNACKS_OFFER
        );
    }

    private AgentArtifact product(int ordinal, String productKey, String offerKey, String label) {
        return new AgentArtifact(
                AgentArtifactType.PRODUCT,
                ordinal,
                productKey,
                label,
                productKey,
                offerKey,
                null,
                null,
                null,
                null,
                "{\"name\":\"" + label + "\",\"offerKey\":\"" + offerKey + "\"}"
        );
    }

    private CartOfferPartitionResult partition(String route, String domain, String offerKey) {
        return new CartOfferPartitionResult(
                route,
                "shopify",
                UUID.nameUUIDFromBytes((route + ":integration").getBytes()),
                route,
                UUID.nameUUIDFromBytes((route + ":merchant").getBytes()),
                domain,
                List.of(new CartOfferPartitionResult.Item(offerKey, 1))
        );
    }

    private CartResult cart(UUID cartId, String domain, String route, long amountMinor) {
        Instant now = Instant.now();
        return new CartResult(
                cartId,
                UUID.nameUUIDFromBytes((route + ":merchant").getBytes()),
                domain,
                "shopify",
                UUID.nameUUIDFromBytes((route + ":integration").getBytes()),
                route,
                route,
                "https://" + domain + "/ucp",
                "remote-" + cartId,
                "https://" + domain + "/checkout",
                "https://" + domain,
                null,
                1,
                Long.toString(amountMinor),
                Long.toString(amountMinor),
                "USD",
                true,
                now,
                now,
                now.plusSeconds(3600),
                now,
                now,
                now,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private CheckoutResult checkout(UUID cartId, UUID attemptId, String domain, long amountMinor) {
        return new CheckoutResult(
                cartId,
                "remote-" + cartId,
                "checkout-" + attemptId,
                attemptId,
                "incomplete",
                "https://" + domain + "/checkout",
                "https://" + domain,
                "2026-04-08",
                amountMinor,
                "USD",
                List.of(),
                CheckoutNextAction.HANDOFF,
                CommerceExecutionRail.MERCHANT_HANDOFF,
                List.of(),
                MerchantExecutionPolicy.unavailable(),
                null
        );
    }

    private void awaitTerminalRun(UUID runId) throws InterruptedException {
        for (int attempt = 0; attempt < 160; attempt++) {
            if (runRepository.findById(runId).map(run -> run.getStatus().terminal()).orElse(false)) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Agent run did not reach a terminal state");
    }
}
