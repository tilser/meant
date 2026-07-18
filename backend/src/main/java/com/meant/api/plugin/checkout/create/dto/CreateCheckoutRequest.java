package com.meant.api.plugin.checkout.create.dto;

import com.meant.api.plugin.checkout.common.dto.CheckoutBuyer;
import com.meant.api.plugin.checkout.common.dto.CheckoutContext;
import com.meant.api.plugin.checkout.extension.buyerconsent.dto.BuyerConsentState;
import com.meant.api.plugin.checkout.extension.fulfillment.dto.CheckoutFulfillment;
import java.util.List;

public record CreateCheckoutRequest(
        String cartId,
        List<LineItem> lineItems,
        CheckoutBuyer buyer,
        BuyerConsentState buyerConsent,
        String currency,
        CheckoutContext context,
        List<String> discountCodes,
        CheckoutFulfillment fulfillment
) {

    public CreateCheckoutRequest(String cartId) {
        this(cartId, List.of());
    }

    public CreateCheckoutRequest(String cartId, List<LineItem> lineItems) {
        this(cartId, lineItems, null, null, null, null, List.of(), null);
    }

    public CreateCheckoutRequest(
            String cartId,
            List<LineItem> lineItems,
            CheckoutBuyer buyer,
            List<String> discountCodes,
            CheckoutFulfillment fulfillment
    ) {
        this(cartId, lineItems, buyer, null, null, null, discountCodes, fulfillment);
    }

    public CreateCheckoutRequest(
            String cartId,
            List<LineItem> lineItems,
            CheckoutBuyer buyer,
            BuyerConsentState buyerConsent,
            String currency,
            List<String> discountCodes,
            CheckoutFulfillment fulfillment
    ) {
        this(cartId, lineItems, buyer, buyerConsent, currency, null, discountCodes, fulfillment);
    }

    public CreateCheckoutRequest {
        lineItems = lineItems == null ? List.of() : List.copyOf(lineItems);
        buyer = buyer == null || buyer.empty() ? null : buyer;
        context = context == null || context.empty() ? null : context;
        discountCodes = discountCodes == null ? List.of() : List.copyOf(discountCodes);
        fulfillment = fulfillment == null || fulfillment.empty() ? null : fulfillment;
    }

    public record LineItem(
            String id,
            String productVariantId,
            Integer quantity
    ) {
        public LineItem(String productVariantId, Integer quantity) {
            this(null, productVariantId, quantity);
        }
    }
}
