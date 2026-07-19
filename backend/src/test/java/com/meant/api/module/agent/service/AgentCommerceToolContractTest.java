package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.entity.AgentConversation;
import com.meant.api.module.agent.repository.AgentConversationRepository;
import com.meant.api.module.agent.service.dto.AgentCartToolArguments;
import com.meant.api.module.agent.service.dto.AgentCheckoutToolArguments;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.cart.service.CartService;
import com.meant.api.module.cart.service.command.CreateCartCommand;
import com.meant.api.module.cart.service.command.UpdateCartCommand;
import com.meant.api.module.cart.service.command.UpdateCheckoutCommand;
import com.meant.api.module.cart.service.dto.CartOfferPartitionResult;
import com.meant.api.module.cart.service.dto.CartResult;
import com.meant.api.module.cart.service.dto.CheckoutResult;
import com.meant.api.module.cart.service.query.GetCheckoutQuery;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class AgentCommerceToolContractTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID CONVERSATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000202");
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void everyCommerceToolSchemaExcludesUserIdentityAndRejectsUnknownProperties() throws Exception {
        AgentMissionToolSupport mission = mock(AgentMissionToolSupport.class);
        AgentCartToolSupport cart = mock(AgentCartToolSupport.class);
        AgentCheckoutToolSupport checkout = mock(AgentCheckoutToolSupport.class);
        List<AgentTool> tools = List.of(
                new AgentMissionCreateTool(mission),
                new AgentMissionUpdateTool(mission),
                new AgentMissionEvaluateCoverageTool(mission),
                new AgentCartGetActiveTool(cart),
                new AgentCartGetTool(cart),
                new AgentCartPrepareTool(cart),
                new AgentCartAddLineTool(cart),
                new AgentCartUpdateLineTool(cart),
                new AgentCartRemoveLineTool(cart),
                new AgentCheckoutPrepareTool(checkout),
                new AgentCheckoutGetTool(checkout),
                new AgentCheckoutUpdateTool(checkout)
        );

        assertThat(tools).extracting(tool -> tool.descriptor().name()).doesNotHaveDuplicates();
        for (AgentTool tool : tools) {
            String schema = tool.descriptor().inputSchemaJson();
            assertThat(schema)
                    .as(tool.descriptor().name())
                    .doesNotContain("userId")
                    .contains("\"additionalProperties\":false");
            assertThat(objectMapper.readTree(schema).isObject()).isTrue();
        }
    }

    @Test
    void prepareCartPropagatesTheTrustedBuyerIpToTheShopifyTransportCommand() {
        AgentConversationRepository conversations = ownedConversationRepository();
        CartService cartService = mock(CartService.class);
        AgentProductReadReferenceService references = mock(AgentProductReadReferenceService.class);
        CartResult result = mock(CartResult.class);
        UUID cartId = UUID.randomUUID();
        when(result.cartId()).thenReturn(cartId);
        when(cartService.partitionSelectedOffers(any())).thenReturn(List.of(new CartOfferPartitionResult(
                "shopify:merchant-1",
                "SHOPIFY",
                null,
                "merchant-1",
                null,
                "running.example",
                List.of(new CartOfferPartitionResult.Item("offer-1", 1))
        )));
        when(cartService.create(any(), any())).thenReturn(result);
        AgentCartToolSupport support = new AgentCartToolSupport(
                conversations,
                references,
                cartService,
                mock(AgentMissionToolSupport.class),
                objectMapper,
                validator,
                mock(AgentJsonSupport.class)
        );

        support.prepare(
                context().withBuyerIp("203.0.113.42"),
                new AgentCartToolArguments.Prepare(List.of(
                        new AgentCartToolArguments.ExactOffer("offer-1", 1)))
        );

        ArgumentCaptor<CreateCartCommand> command = ArgumentCaptor.forClass(CreateCartCommand.class);
        verify(cartService).create(command.capture(), any());
        assertThat(command.getValue().buyerIp()).isEqualTo("203.0.113.42");
    }

    @Test
    void cartMutationInjectsTheContextOwnerAndUsesOnlyLocalCartLineIdentifiers() {
        AgentConversationRepository conversations = ownedConversationRepository();
        CartService cartService = mock(CartService.class);
        AgentProductReadReferenceService references = mock(AgentProductReadReferenceService.class);
        CartResult result = mock(CartResult.class);
        UUID idempotencyKey = UUID.randomUUID();
        when(cartService.update(any(), org.mockito.ArgumentMatchers.eq(idempotencyKey))).thenReturn(result);
        AgentCartToolSupport support = new AgentCartToolSupport(
                conversations,
                references,
                cartService,
                mock(AgentMissionToolSupport.class),
                objectMapper,
                validator,
                mock(AgentJsonSupport.class)
        );
        UUID cartId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();

        assertThat(support.updateLine(
                context().withIdempotencyKey(idempotencyKey).withBuyerIp("203.0.113.42"),
                new AgentCartToolArguments.UpdateLine(cartId, lineId, 3)
        )).isSameAs(result);

        ArgumentCaptor<UpdateCartCommand> command = ArgumentCaptor.forClass(UpdateCartCommand.class);
        verify(cartService).update(command.capture(), org.mockito.ArgumentMatchers.eq(idempotencyKey));
        verify(references).requireCartLine(any(), org.mockito.ArgumentMatchers.eq(cartId),
                org.mockito.ArgumentMatchers.eq(lineId));
        assertThat(command.getValue().userId()).isEqualTo(USER_ID);
        assertThat(command.getValue().cartId()).isEqualTo(cartId);
        assertThat(command.getValue().buyerIp()).isEqualTo("203.0.113.42");
        assertThat(command.getValue().updateItems()).singleElement().satisfies(item -> {
            assertThat(item.cartLineId()).isEqualTo(lineId);
            assertThat(item.remoteCartLineId()).isNull();
            assertThat(item.quantity()).isEqualTo(3);
        });
    }

    @Test
    void getCheckoutUsesTheReadOnlyDomainPathAndNeverPreparesCheckout() {
        AgentConversationRepository conversations = ownedConversationRepository();
        CartService cartService = mock(CartService.class);
        AgentProductReadReferenceService references = mock(AgentProductReadReferenceService.class);
        CheckoutResult result = mock(CheckoutResult.class);
        when(cartService.getCheckout(any())).thenReturn(result);
        AgentCheckoutToolSupport support = new AgentCheckoutToolSupport(
                conversations,
                references,
                cartService,
                mock(AgentMissionToolSupport.class),
                objectMapper,
                validator,
                mock(AgentJsonSupport.class)
        );
        UUID cartId = UUID.randomUUID();

        assertThat(support.get(
                context().withBuyerIp("203.0.113.42"),
                new AgentCheckoutToolArguments.Get(cartId, true)
        )).isSameAs(result);

        ArgumentCaptor<GetCheckoutQuery> query = ArgumentCaptor.forClass(GetCheckoutQuery.class);
        verify(cartService).getCheckout(query.capture());
        verify(cartService, never()).checkout(any());
        verify(references).requireCart(any(), org.mockito.ArgumentMatchers.eq(cartId));
        assertThat(query.getValue().userId()).isEqualTo(USER_ID);
        assertThat(query.getValue().cartId()).isEqualTo(cartId);
        assertThat(query.getValue().refresh()).isTrue();
        assertThat(query.getValue().buyerIp()).isEqualTo("203.0.113.42");
    }

    @Test
    void prepareAndUpdateCheckoutPropagateTheTrustedBuyerIp() {
        AgentConversationRepository conversations = ownedConversationRepository();
        CartService cartService = mock(CartService.class);
        AgentProductReadReferenceService references = mock(AgentProductReadReferenceService.class);
        UUID cartId = UUID.randomUUID();
        CheckoutResult result = mock(CheckoutResult.class);
        when(result.cartId()).thenReturn(cartId);
        when(result.messages()).thenReturn(List.of());
        when(cartService.checkout(any(GetCheckoutQuery.class), any())).thenReturn(result);
        when(cartService.updateCheckout(any(UpdateCheckoutCommand.class), any())).thenReturn(result);
        AgentCheckoutToolSupport support = new AgentCheckoutToolSupport(
                conversations,
                references,
                cartService,
                mock(AgentMissionToolSupport.class),
                objectMapper,
                validator,
                mock(AgentJsonSupport.class)
        );
        AgentToolExecutionContext context = context()
                .withBuyerIp("203.0.113.42")
                .withIdempotencyKey(UUID.randomUUID());

        support.prepare(context, new AgentCheckoutToolArguments.Prepare(List.of(cartId)));
        support.update(context, new AgentCheckoutToolArguments.Update(
                cartId,
                new AgentCheckoutToolArguments.Buyer(
                        "buyer@example.test", "David", "Tilser", "+420123456789"),
                new AgentCheckoutToolArguments.PostalAddress(
                        "Main Street 1", null, "Prague", "Prague", "11000", "CZ"),
                List.of("RUN10")
        ));

        ArgumentCaptor<GetCheckoutQuery> prepareQuery = ArgumentCaptor.forClass(GetCheckoutQuery.class);
        ArgumentCaptor<UpdateCheckoutCommand> updateCommand =
                ArgumentCaptor.forClass(UpdateCheckoutCommand.class);
        verify(cartService).checkout(prepareQuery.capture(), any());
        verify(cartService).updateCheckout(updateCommand.capture(), any());
        assertThat(prepareQuery.getValue().buyerIp()).isEqualTo("203.0.113.42");
        assertThat(updateCommand.getValue().buyerIp()).isEqualTo("203.0.113.42");
    }

    private AgentConversationRepository ownedConversationRepository() {
        AgentConversationRepository repository = mock(AgentConversationRepository.class);
        when(repository.findByIdAndUserId(CONVERSATION_ID, USER_ID))
                .thenReturn(Optional.of(AgentConversation.create(USER_ID, "Test", java.time.Instant.now())));
        return repository;
    }

    private AgentToolExecutionContext context() {
        return new AgentToolExecutionContext(
                USER_ID,
                CONVERSATION_ID,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "test"
        );
    }
}
