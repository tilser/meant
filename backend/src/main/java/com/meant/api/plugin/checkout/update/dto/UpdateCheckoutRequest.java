package com.meant.api.plugin.checkout.update.dto;

import com.meant.api.plugin.checkout.common.dto.CheckoutBuyer;
import com.meant.api.plugin.checkout.common.dto.CheckoutContext;
import com.meant.api.plugin.checkout.extension.buyerconsent.dto.BuyerConsentState;
import com.meant.api.plugin.checkout.extension.fulfillment.dto.CheckoutFulfillment;
import java.util.List;

public record UpdateCheckoutRequest(
        String checkoutId,
        List<LineItem> lineItems,
        CheckoutBuyer buyer,
        BuyerConsentState buyerConsent,
        String email,
        String currency,
        CheckoutContext context,
        List<String> discountCodes,
        CheckoutFulfillment fulfillment
) {

    public UpdateCheckoutRequest {
        lineItems = lineItems == null ? List.of() : List.copyOf(lineItems);
        buyer = buyer == null || buyer.empty() ? null : buyer;
        context = context == null || context.empty() ? null : context;
        discountCodes = discountCodes == null ? null : List.copyOf(discountCodes);
        fulfillment = fulfillment == null || fulfillment.empty() ? null : fulfillment;
    }

    public record LineItem(
            String id,
            String productVariantId,
            Integer quantity
    ) {
    }
}
