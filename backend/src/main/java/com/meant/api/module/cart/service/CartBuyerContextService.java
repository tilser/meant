package com.meant.api.module.cart.service;

import com.meant.api.module.user.service.UserCommerceContextService;
import com.meant.api.module.user.service.dto.UserCommerceContextResult;
import com.meant.api.plugin.cart.common.dto.CartContext;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Builds the UCP {@code context} object (localization + market hints) for cart and checkout
 * tool calls. Commerce providers can allocate inventory per market; without an
 * {@code address_country} hint merchants may drop otherwise purchasable line items, so every
 * cart and checkout call sends the buyer's country.
 */
@Service
@RequiredArgsConstructor
public class CartBuyerContextService {

    private final UserCommerceContextService userCommerceContextService;

    public CartContext buyerContext(UUID userId) {
        return buyerContext(userCommerceContextService.find(userId));
    }

    public CartContext buyerContext(UserCommerceContextResult commerceContext) {
        return new CartContext(commerceContext == null ? null : commerceContext.countryCode());
    }
}
