package com.meant.api.module.cart.service.port;

import com.meant.api.module.cart.service.dto.CartRoutingTarget;
import com.meant.api.module.user.service.dto.ResolvedSelectedOffer;
import java.util.Optional;

/** Provider adapter for offers that legitimately have no local MerchantIntegration. */
public interface ExternalOfferCartRoutingProvider {
    boolean supports(ResolvedSelectedOffer offer);

    Optional<CartRoutingTarget> resolve(ResolvedSelectedOffer offer);

    default boolean supportsPersisted(CartRoutingTarget target) {
        return false;
    }

    default Optional<CartRoutingTarget> restore(CartRoutingTarget target) {
        return Optional.empty();
    }

    default Optional<CartRoutingTarget> restoreForCheckout(CartRoutingTarget target) {
        return restore(target);
    }
}
