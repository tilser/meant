package com.meant.api.module.cart.service;

import com.meant.api.module.cart.constant.CheckoutNextAction;
import com.meant.api.module.cart.constant.EmbeddedCheckoutBootstrapAction;
import com.meant.api.module.cart.entity.Cart;
import com.meant.api.module.cart.service.dto.CheckoutResult;
import com.meant.api.module.cart.service.dto.EmbeddedCheckoutBootstrapResult;
import com.meant.api.module.cart.service.query.GetCheckoutQuery;
import com.meant.api.module.checkout.exception.EmbeddedCheckoutException;
import com.meant.api.module.checkout.properties.EmbeddedCheckoutProperties;
import com.meant.api.module.checkout.service.EmbeddedCheckoutSessionStore;
import com.meant.api.module.checkout.service.command.CreateEmbeddedCheckoutSessionCommand;
import com.meant.api.module.checkout.service.command.UseEmbeddedCheckoutSessionCommand;
import com.meant.api.module.checkout.service.dto.EmbeddedCheckoutSessionBinding;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
@Slf4j
public class EmbeddedCheckoutBootstrapService {
    private final CartService cartService;
    private final CartPersistenceService cartPersistenceService;
    private final EmbeddedCheckoutSessionStore sessionStore;
    private final EmbeddedCheckoutOriginPolicy originPolicy;
    private final EmbeddedCheckoutProperties properties;

    public EmbeddedCheckoutBootstrapResult bootstrap(
            @NotNull UUID cartId, @NotNull UUID userId, @NotNull String origin) {
        return bootstrap(cartId, userId, origin, null);
    }

    public EmbeddedCheckoutBootstrapResult bootstrap(
            @NotNull UUID cartId,
            @NotNull UUID userId,
            @NotNull String origin,
            String buyerIp
    ) {
        String allowedOrigin = originPolicy.requireAllowed(origin);
        CheckoutResult checkout = cartService.checkout(new GetCheckoutQuery(cartId, userId, true, buyerIp));
        Cart cart = cartPersistenceService.findCart(cartId, userId);
        EmbeddedCheckoutBootstrapResult result = checkout.nextAction() == CheckoutNextAction.OPEN_EMBEDDED_CHECKOUT
                ? embedded(cart, checkout, allowedOrigin)
                : alternative(cart, checkout);
        log.info(
                "Embedded checkout bootstrap action={} nextAction={} selectedRail={} protocol={} "
                        + "checkoutUrlPresent={} fallbackUrlPresent={} reason={}",
                result.action(),
                checkout.nextAction(),
                checkout.selectedRail(),
                result.protocolVersion(),
                result.checkoutUrl() != null,
                result.fallbackContinueUrl() != null,
                result.reason()
        );
        return result;
    }

    public CheckoutResult complete(
            @NotNull UUID cartId, @NotNull UUID sessionId, @NotNull UUID userId, @NotNull String origin) {
        return complete(cartId, sessionId, userId, origin, null);
    }

    public CheckoutResult complete(
            @NotNull UUID cartId,
            @NotNull UUID sessionId,
            @NotNull UUID userId,
            @NotNull String origin,
            String buyerIp
    ) {
        String allowedOrigin = originPolicy.requireAllowed(origin);
        Cart before = cartPersistenceService.findCart(cartId, userId);
        UseEmbeddedCheckoutSessionCommand command = useCommand(sessionId, userId, before, allowedOrigin);
        sessionStore.requireActive(command);
        CheckoutResult refreshed = cartService.checkout(new GetCheckoutQuery(cartId, userId, true, buyerIp));
        if (!same(refreshed.checkoutId(), before.getCheckoutId())
                || refreshed.nextAction() != CheckoutNextAction.DONE
                || !"completed".equals(normalized(refreshed.status()))) {
            throw EmbeddedCheckoutException.conflict("Embedded checkout completion is not verified by the provider");
        }
        sessionStore.complete(command);
        return refreshed;
    }

    public void cancel(
            @NotNull UUID cartId, @NotNull UUID sessionId, @NotNull UUID userId, @NotNull String origin) {
        String allowedOrigin = originPolicy.requireAllowed(origin);
        Cart cart = cartPersistenceService.findCart(cartId, userId);
        sessionStore.cancel(useCommand(sessionId, userId, cart, allowedOrigin));
    }

    private EmbeddedCheckoutBootstrapResult embedded(Cart cart, CheckoutResult checkout, String allowedOrigin) {
        String checkoutUrl = embeddedUrl(checkout);
        if (checkout.checkoutId() == null || checkoutUrl == null || checkoutUrl.isBlank()
                || cart.getRoutingScopeKey() == null) {
            return handoff(cart, checkout, "Merchant did not confirm embedded checkout for this session");
        }
        String version = checkout.embeddedCheckout() == null
                ? properties.supportedProtocolVersion()
                : checkout.embeddedCheckout().protocolVersion();
        if (!properties.supportedProtocolVersion().equals(version)) {
            return handoff(cart, checkout, "Embedded checkout protocol version is unsupported");
        }
        if (checkout.embeddedCheckout() != null
                && checkout.embeddedCheckout().authenticationType() != null
                && !checkout.embeddedCheckout().authenticationType().isBlank()) {
            return handoff(cart, checkout, "Merchant requires an unsupported embedded authentication exchange");
        }
        String fallbackContinueUrl = checkout.continueUrl();
        EmbeddedCheckoutSessionBinding session = sessionStore.create(new CreateEmbeddedCheckoutSessionCommand(
                cart.getUserId(), cart.getId(), checkout.checkoutId(), cart.getMerchantIntegrationId(),
                cart.getRoutingScopeKey(), allowedOrigin, version));
        return new EmbeddedCheckoutBootstrapResult(
                EmbeddedCheckoutBootstrapAction.EMBEDDED, session.sessionId(), cart.getId(), checkout.checkoutId(),
                checkoutUrl, fallbackContinueUrl, version, null, List.of(), session.expiresAt(),
                cart.getProvider(), cart.getMerchantDomain(), null);
    }

    private String embeddedUrl(CheckoutResult checkout) {
        return checkout.continueUrl() == null || checkout.continueUrl().isBlank()
                ? checkout.checkoutUrl() : checkout.continueUrl();
    }

    private EmbeddedCheckoutBootstrapResult alternative(Cart cart, CheckoutResult checkout) {
        if (checkout.nextAction() == CheckoutNextAction.COMPLETE_CHECKOUT) {
            return result(EmbeddedCheckoutBootstrapAction.DIRECT_COMPLETE, cart, checkout, null);
        }
        if (checkout.nextAction() == CheckoutNextAction.WAIT) {
            return result(EmbeddedCheckoutBootstrapAction.WAIT, cart, checkout, null);
        }
        if (checkout.nextAction() == CheckoutNextAction.DONE) {
            return result(EmbeddedCheckoutBootstrapAction.COMPLETED, cart, checkout, null);
        }
        if (checkout.continueUrl() != null && !checkout.continueUrl().isBlank()) {
            return handoff(cart, checkout, "Embedded checkout is unavailable for this session");
        }
        return result(EmbeddedCheckoutBootstrapAction.UNAVAILABLE, cart, checkout,
                "No embedded or external checkout route is available");
    }

    private EmbeddedCheckoutBootstrapResult handoff(Cart cart, CheckoutResult checkout, String reason) {
        if (checkout.continueUrl() == null || checkout.continueUrl().isBlank()) {
            return result(EmbeddedCheckoutBootstrapAction.UNAVAILABLE, cart, checkout, reason);
        }
        return new EmbeddedCheckoutBootstrapResult(
                EmbeddedCheckoutBootstrapAction.EXTERNAL_HANDOFF, null, cart.getId(), checkout.checkoutId(),
                null, checkout.continueUrl(), null, null, List.of(), null,
                cart.getProvider(), cart.getMerchantDomain(), reason);
    }

    private EmbeddedCheckoutBootstrapResult result(
            EmbeddedCheckoutBootstrapAction action, Cart cart, CheckoutResult checkout, String reason) {
        return new EmbeddedCheckoutBootstrapResult(action, null, cart.getId(), checkout.checkoutId(), null,
                null, null, null, List.of(), null,
                cart.getProvider(), cart.getMerchantDomain(), reason);
    }

    private UseEmbeddedCheckoutSessionCommand useCommand(
            UUID sessionId, UUID userId, Cart cart, String allowedOrigin) {
        if (cart.getCheckoutId() == null || cart.getCheckoutId().isBlank()) {
            throw EmbeddedCheckoutException.conflict("Cart has no active checkout session");
        }
        return new UseEmbeddedCheckoutSessionCommand(
                sessionId, userId, cart.getId(), cart.getCheckoutId(), cart.getMerchantIntegrationId(),
                cart.getRoutingScopeKey(), allowedOrigin);
    }

    private boolean same(String first, String second) {
        return first != null && first.equals(second);
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
