package com.meant.api.module.agent.service.tool;

import com.meant.api.common.constant.ApiErrorCode;
import com.meant.api.common.exception.ApiException;
import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.repository.AgentConversationRepository;
import com.meant.api.module.agent.service.AgentCartArtifacts;
import com.meant.api.module.agent.service.AgentJsonSupport;
import com.meant.api.module.agent.service.AgentProductReadReferenceService;
import com.meant.api.module.agent.service.dto.AgentArtifact;
import com.meant.api.module.agent.service.dto.AgentCartResult;
import com.meant.api.module.agent.service.dto.AgentCartToolArguments;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.cart.service.CartRouteKey;
import com.meant.api.module.cart.service.CartService;
import com.meant.api.module.cart.service.command.CreateCartCommand;
import com.meant.api.module.cart.service.command.UpdateCartCommand;
import com.meant.api.module.cart.service.dto.CartOfferPartitionResult;
import com.meant.api.module.cart.service.dto.CartResult;
import com.meant.api.module.cart.service.query.FindActiveCartByRoutingScopeQuery;
import com.meant.api.module.cart.service.query.GetCartQuery;
import com.meant.api.module.cart.service.query.ListActiveCartsQuery;
import com.meant.api.module.cart.service.query.PartitionSelectedOffersQuery;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
class AgentCartToolSupport {

    private static final int MAX_ARGUMENT_BYTES = 64_000;

    private final AgentConversationRepository conversationRepository;
    private final AgentProductReadReferenceService referenceService;
    private final CartService cartService;
    private final AgentMissionToolSupport missionSupport;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final AgentJsonSupport jsonSupport;

    <T> T arguments(String json, Class<T> type) {
        if (json == null || json.isBlank() || json.length() > MAX_ARGUMENT_BYTES) {
            throw invalid("Tool arguments must be a bounded JSON object.");
        }
        try {
            T value = objectMapper.readValue(json, type);
            if (value == null) {
                throw invalid("Tool arguments must be a JSON object.");
            }
            Set<ConstraintViolation<T>> violations = validator.validate(value);
            if (!violations.isEmpty()) {
                String field = violations.stream()
                        .map(violation -> violation.getPropertyPath().toString())
                        .sorted()
                        .findFirst()
                        .orElse("arguments");
                throw invalid("Invalid tool argument: " + field + ".");
            }
            return value;
        } catch (AgentException exception) {
            throw exception;
        } catch (JacksonException exception) {
            throw invalid("Tool arguments are not valid JSON.");
        }
    }

    List<CartResult> active(AgentToolExecutionContext context, AgentCartToolArguments.GetActive arguments) {
        ownedContext(context);
        int limit = arguments.limit() == null ? 10 : arguments.limit();
        Map<String, CartResult> currentByRoute = new LinkedHashMap<>();
        cartService.listActive(new ListActiveCartsQuery(context.userId(), limit))
                .forEach(cart -> currentByRoute.putIfAbsent(CartRouteKey.from(cart), cart));
        return currentByRoute.values().stream().limit(limit).toList();
    }

    CartResult get(AgentToolExecutionContext context, AgentCartToolArguments.Get arguments) {
        ownedContext(context);
        referenceService.requireCart(context, arguments.cartId());
        return cartService.get(new GetCartQuery(
                arguments.cartId(), context.userId(), Boolean.TRUE.equals(arguments.refresh()), context.buyerIp()));
    }

    AgentCartResult prepare(AgentToolExecutionContext context, AgentCartToolArguments.Prepare arguments) {
        ownedContext(context);
        arguments.offers().forEach(item -> referenceService.requireCartOffer(context, item.offerKey()));
        List<CartOfferPartitionResult> partitions = cartService.partitionSelectedOffers(
                new PartitionSelectedOffersQuery(
                        context.userId(),
                        arguments.offers().stream()
                                .map(item -> new PartitionSelectedOffersQuery.Item(item.offerKey(), item.quantity()))
                                .toList()
                ));
        List<AgentCartResult.Cart> carts = new ArrayList<>();
        List<AgentCartResult.Failure> failures = new ArrayList<>();
        List<UUID> cartIds = new ArrayList<>();
        for (CartOfferPartitionResult partition : partitions) {
            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException("Cart preparation was cancelled");
            }
            try {
                CartResult prepared = preparePartition(context, partition);
                carts.add(AgentCartResult.Cart.from(prepared));
                cartIds.add(prepared.cartId());
            } catch (CancellationException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new CancellationException("Cart preparation was cancelled");
                }
                failures.add(new AgentCartResult.Failure(
                        partition.routingScopeKey(),
                        partition.provider(),
                        partition.merchantDomain(),
                        safeMessage(exception, "This merchant cart could not be prepared.")));
            }
        }
        missionSupport.attachCartReferences(context, cartIds);
        return new AgentCartResult(carts, failures);
    }

    private CartResult preparePartition(
            AgentToolExecutionContext context,
            CartOfferPartitionResult partition
    ) {
        UUID idempotencyKey = scopedIdempotencyKey(context, partition.routingScopeKey());
        CartResult active = cartService.findActiveByRoutingScope(new FindActiveCartByRoutingScopeQuery(
                context.userId(), partition.routingScopeKey())).orElse(null);
        if (active != null) {
            return cartService.update(updateCommand(
                    active.cartId(),
                    context.userId(),
                    partition.items().stream()
                            .map(item -> new UpdateCartCommand.AddItem(item.offerKey(), item.quantity()))
                            .toList(),
                    List.of(),
                    List.of(),
                    context.buyerIp()
            ), idempotencyKey);
        }
        return cartService.create(new CreateCartCommand(
                context.userId(),
                null,
                null,
                partition.items().stream()
                        .map(item -> new CreateCartCommand.AddItem(item.offerKey(), item.quantity()))
                        .toList(),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                context.buyerIp()
        ), idempotencyKey);
    }

    CartResult addLine(AgentToolExecutionContext context, AgentCartToolArguments.AddLine arguments) {
        ownedContext(context);
        referenceService.requireCart(context, arguments.cartId());
        referenceService.requireCartOffer(context, arguments.offerKey());
        return cartService.update(updateCommand(
                arguments.cartId(),
                context.userId(),
                List.of(new UpdateCartCommand.AddItem(arguments.offerKey(), arguments.quantity())),
                List.of(),
                List.of(),
                context.buyerIp()
        ), context.idempotencyKey());
    }

    CartResult updateLine(AgentToolExecutionContext context, AgentCartToolArguments.UpdateLine arguments) {
        ownedContext(context);
        referenceService.requireCartLine(context, arguments.cartId(), arguments.cartLineId());
        return cartService.update(updateCommand(
                arguments.cartId(),
                context.userId(),
                List.of(),
                List.of(new UpdateCartCommand.UpdateItem(arguments.cartLineId(), null, arguments.quantity())),
                List.of(),
                context.buyerIp()
        ), context.idempotencyKey());
    }

    CartResult removeLine(AgentToolExecutionContext context, AgentCartToolArguments.RemoveLine arguments) {
        ownedContext(context);
        referenceService.requireCartLine(context, arguments.cartId(), arguments.cartLineId());
        return cartService.update(updateCommand(
                arguments.cartId(),
                context.userId(),
                List.of(),
                List.of(),
                List.of(arguments.cartLineId()),
                context.buyerIp()
        ), context.idempotencyKey());
    }

    String json(Object value) {
        return jsonSupport.write(value);
    }

    List<AgentArtifact> artifacts(AgentCartResult result) {
        return AgentCartArtifacts.from(result, jsonSupport);
    }

    private UpdateCartCommand updateCommand(
            UUID cartId,
            UUID userId,
            List<UpdateCartCommand.AddItem> additions,
            List<UpdateCartCommand.UpdateItem> updates,
            List<UUID> removals,
            String buyerIp
    ) {
        return new UpdateCartCommand(
                cartId,
                userId,
                additions,
                updates,
                removals,
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                buyerIp
        );
    }

    private void ownedContext(AgentToolExecutionContext context) {
        if (context == null || context.userId() == null || context.conversationId() == null
                || context.triggeringMessageId() == null
                || conversationRepository.findByIdAndUserId(context.conversationId(), context.userId()).isEmpty()) {
            throw AgentException.notFound();
        }
    }

    private String safeMessage(RuntimeException exception, String fallback) {
        if (exception instanceof ApiException apiException
                && apiException.getSafeMessage() != null
                && !apiException.getSafeMessage().isBlank()) {
            return apiException.getSafeMessage();
        }
        return fallback;
    }

    private UUID scopedIdempotencyKey(AgentToolExecutionContext context, String scope) {
        if (context.idempotencyKey() == null) {
            return null;
        }
        return UUID.nameUUIDFromBytes(
                (context.idempotencyKey() + ":" + scope).getBytes(StandardCharsets.UTF_8)
        );
    }

    private AgentException invalid(String message) {
        return new AgentException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, message);
    }
}
