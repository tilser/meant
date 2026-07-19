package com.meant.api.module.cart.service;

import com.meant.api.module.cart.entity.Cart;
import com.meant.api.module.cart.service.dto.CartResult;
import java.util.Locale;
import java.util.UUID;

/** Stable identity used to keep only the newest local cart for one remote-cart route. */
public final class CartRouteKey {

    private CartRouteKey() {
    }

    public static String from(Cart cart) {
        return from(
                cart.getId(),
                cart.getRoutingScopeKey(),
                cart.getMerchantIntegrationId(),
                cart.getMerchantId(),
                cart.getProvider(),
                cart.getExternalMerchantId(),
                cart.getMerchantDomain()
        );
    }

    public static String from(CartResult cart) {
        return from(
                cart.cartId(),
                cart.routingScopeKey(),
                cart.merchantIntegrationId(),
                cart.merchantId(),
                cart.provider(),
                cart.externalMerchantId(),
                cart.merchantDomain()
        );
    }

    private static String from(
            UUID cartId,
            String routingScopeKey,
            UUID merchantIntegrationId,
            UUID merchantId,
            String provider,
            String externalMerchantId,
            String merchantDomain
    ) {
        if (routingScopeKey != null && !routingScopeKey.isBlank()) {
            return "routing:" + routingScopeKey.toLowerCase(Locale.ROOT);
        }
        if (merchantIntegrationId != null) {
            return "integration:" + merchantIntegrationId;
        }
        if (merchantId != null) {
            return "merchant:" + merchantId;
        }
        String normalizedProvider = provider == null ? "" : provider.toLowerCase(Locale.ROOT);
        if (externalMerchantId != null && !externalMerchantId.isBlank()) {
            return "external:" + normalizedProvider + ":" + externalMerchantId.toLowerCase(Locale.ROOT);
        }
        if (merchantDomain != null && !merchantDomain.isBlank()) {
            return "domain:" + normalizedProvider + ":" + merchantDomain.toLowerCase(Locale.ROOT);
        }
        return "cart:" + cartId;
    }
}
