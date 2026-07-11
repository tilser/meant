package com.meant.api.module.cart.service;

import com.meant.api.module.user.service.UserCommerceContextService;
import com.meant.api.module.user.service.dto.UserCommerceContextResult;
import java.util.LinkedHashMap;
import java.util.Map;
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

    public Map<String, Object> buyerContext(UUID userId) {
        return buyerContext(userCommerceContextService.find(userId));
    }

    public Map<String, Object> buyerContext(UserCommerceContextResult commerceContext) {
        Map<String, Object> context = new LinkedHashMap<>();
        if (commerceContext != null && commerceContext.countryCode() != null) {
            context.put("address_country", commerceContext.countryCode());
        }
        return context;
    }
}
