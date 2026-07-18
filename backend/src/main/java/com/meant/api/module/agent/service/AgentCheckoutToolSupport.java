package com.meant.api.module.agent.service;

import com.meant.api.common.constant.ApiErrorCode;
import com.meant.api.common.exception.ApiException;
import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.repository.AgentConversationRepository;
import com.meant.api.module.agent.service.dto.AgentArtifact;
import com.meant.api.module.agent.service.dto.AgentCheckoutResult;
import com.meant.api.module.agent.service.dto.AgentCheckoutToolArguments;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.cart.service.CartService;
import com.meant.api.module.cart.service.command.UpdateCheckoutCommand;
import com.meant.api.module.cart.service.dto.CheckoutResult;
import com.meant.api.module.cart.service.query.GetCheckoutQuery;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
class AgentCheckoutToolSupport {

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

    AgentCheckoutResult prepare(
            AgentToolExecutionContext context,
            AgentCheckoutToolArguments.Prepare arguments
    ) {
        ownedContext(context);
        arguments.cartIds().forEach(cartId -> referenceService.requireCart(context, cartId));
        List<AgentCheckoutResult.Checkout> checkouts = new ArrayList<>();
        List<AgentCheckoutResult.Failure> failures = new ArrayList<>();
        List<UUID> preparedCartIds = new ArrayList<>();
        List<UUID> checkoutAttemptIds = new ArrayList<>();
        for (UUID cartId : new LinkedHashSet<>(arguments.cartIds())) {
            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException("Checkout preparation was cancelled");
            }
            try {
                CheckoutResult prepared = cartService.checkout(
                        new GetCheckoutQuery(cartId, context.userId(), false),
                        scopedIdempotencyKey(context, cartId)
                );
                checkouts.add(AgentCheckoutResult.Checkout.from(prepared));
                preparedCartIds.add(prepared.cartId());
                if (prepared.checkoutAttemptId() != null) {
                    checkoutAttemptIds.add(prepared.checkoutAttemptId());
                }
            } catch (CancellationException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new CancellationException("Checkout preparation was cancelled");
                }
                failures.add(new AgentCheckoutResult.Failure(
                        cartId,
                        safeMessage(exception, "Checkout could not be prepared for this cart.")));
            }
        }
        missionSupport.attachCartReferences(context, preparedCartIds);
        missionSupport.attachCheckoutReferences(context, checkoutAttemptIds);
        return new AgentCheckoutResult(checkouts, failures);
    }

    CheckoutResult get(AgentToolExecutionContext context, AgentCheckoutToolArguments.Get arguments) {
        ownedContext(context);
        referenceService.requireCart(context, arguments.cartId());
        return cartService.getCheckout(new GetCheckoutQuery(
                arguments.cartId(), context.userId(), Boolean.TRUE.equals(arguments.refresh())));
    }

    CheckoutResult update(AgentToolExecutionContext context, AgentCheckoutToolArguments.Update arguments) {
        ownedContext(context);
        referenceService.requireCart(context, arguments.cartId());
        return cartService.updateCheckout(new UpdateCheckoutCommand(
                arguments.cartId(),
                context.userId(),
                new UpdateCheckoutCommand.Buyer(
                        arguments.buyer().email(),
                        arguments.buyer().firstName(),
                        arguments.buyer().lastName(),
                        arguments.buyer().phoneNumber()
                ),
                new UpdateCheckoutCommand.PostalAddress(
                        arguments.shippingAddress().streetAddress(),
                        arguments.shippingAddress().extendedAddress(),
                        arguments.shippingAddress().addressLocality(),
                        arguments.shippingAddress().addressRegion(),
                        arguments.shippingAddress().postalCode(),
                        arguments.shippingAddress().addressCountry()
                ),
                arguments.discountCodes() == null ? List.of() : arguments.discountCodes(),
                null
        ), context.idempotencyKey());
    }

    String json(Object value) {
        return jsonSupport.write(value);
    }

    List<AgentArtifact> artifacts(AgentCheckoutResult result) {
        List<AgentArtifact> artifacts = new ArrayList<>();
        int ordinal = 1;
        for (AgentCheckoutResult.Checkout checkout : result.checkouts()) {
            String stableKey = checkout.checkoutAttemptId() == null
                    ? "checkout-cart:" + checkout.cartId()
                    : "checkout:" + checkout.checkoutAttemptId();
            artifacts.add(new AgentArtifact(
                    AgentArtifactType.CHECKOUT,
                    ordinal++,
                    stableKey,
                    "Checkout for cart " + checkout.cartId(),
                    null,
                    null,
                    null,
                    checkout.cartId(),
                    null,
                    checkout.checkoutAttemptId(),
                    json(checkout)
            ));
        }
        return List.copyOf(artifacts);
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

    private UUID scopedIdempotencyKey(AgentToolExecutionContext context, UUID cartId) {
        if (context.idempotencyKey() == null) {
            return null;
        }
        return UUID.nameUUIDFromBytes(
                (context.idempotencyKey() + ":" + cartId).getBytes(StandardCharsets.UTF_8)
        );
    }

    private AgentException invalid(String message) {
        return new AgentException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, message);
    }
}
