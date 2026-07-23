package com.meant.api.module.cart.service;

import static com.meant.api.common.util.CollectionUtils.safeList;
import static com.meant.api.module.cart.service.CheckoutFulfillmentSupport.firstText;
import static com.meant.api.module.cart.service.CheckoutFulfillmentSupport.hasText;

import com.meant.api.module.cart.entity.Cart;
import com.meant.api.module.cart.entity.CartLine;
import com.meant.api.module.cart.exception.CartException;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
import com.meant.api.plugin.checkout.update.dto.UpdateCheckoutRequest;
import java.util.ArrayList;
import java.util.List;

/** Matches persisted cart lines to the identities returned by the remote checkout. */
final class CheckoutLineItemMapper {

    private CheckoutLineItemMapper() {
    }

    static List<UpdateCheckoutRequest.LineItem> updateLineItems(
            Cart cart,
            UcpCheckoutResponse currentResponse,
            CheckoutResultMapper checkoutResultMapper
    ) {
        List<UcpCheckoutResponse.CheckoutLineItem> checkoutLines =
                new ArrayList<>(checkoutLineItems(currentResponse));
        if (checkoutLines.isEmpty()) {
            checkoutLines.addAll(storedCheckoutLineItems(cart, checkoutResultMapper));
        }
        List<UpdateCheckoutRequest.LineItem> lineItems = new ArrayList<>();
        for (CartLine line : cart.getLines()) {
            UcpCheckoutResponse.CheckoutLineItem checkoutLine = takeMatchingCheckoutLine(checkoutLines, line);
            String checkoutLineId = checkoutLineId(checkoutLine);
            if (!hasText(checkoutLineId)) {
                throw CartException.rejected("Checkout line identity is unavailable after refresh");
            }
            lineItems.add(new UpdateCheckoutRequest.LineItem(
                    checkoutLineId,
                    line.getProductVariantId(),
                    line.getQuantity()
            ));
        }
        if (!checkoutLines.isEmpty()) {
            throw CartException.rejected("Checkout lines no longer match the cart");
        }
        return List.copyOf(lineItems);
    }

    private static List<UcpCheckoutResponse.CheckoutLineItem> checkoutLineItems(UcpCheckoutResponse response) {
        UcpCheckoutResponse.Checkout checkout = response == null ? null : response.resolvedCheckout();
        return checkout == null ? List.of() : safeList(checkout.lineItems());
    }

    private static List<UcpCheckoutResponse.CheckoutLineItem> storedCheckoutLineItems(
            Cart cart,
            CheckoutResultMapper checkoutResultMapper
    ) {
        return checkoutLineItems(checkoutResultMapper.parseStoredResponse(cart.getRawCheckoutResponse()));
    }

    private static UcpCheckoutResponse.CheckoutLineItem takeMatchingCheckoutLine(
            List<UcpCheckoutResponse.CheckoutLineItem> checkoutLines,
            CartLine cartLine
    ) {
        int matchingIndex = matchingCheckoutLineIndex(checkoutLines, cartLine);
        return matchingIndex < 0 ? null : checkoutLines.remove(matchingIndex);
    }

    private static int matchingCheckoutLineIndex(
            List<UcpCheckoutResponse.CheckoutLineItem> checkoutLines,
            CartLine cartLine
    ) {
        for (int index = 0; index < checkoutLines.size(); index++) {
            UcpCheckoutResponse.CheckoutLineItem checkoutLine = checkoutLines.get(index);
            if (checkoutLine != null && sameVariant(cartLine, checkoutLine)) {
                return index;
            }
        }
        return -1;
    }

    private static boolean sameVariant(
            CartLine cartLine,
            UcpCheckoutResponse.CheckoutLineItem checkoutLine
    ) {
        return hasText(cartLine.getProductVariantId())
                && cartLine.getProductVariantId().equals(checkoutLine.resolvedVariantId());
    }

    private static String checkoutLineId(UcpCheckoutResponse.CheckoutLineItem checkoutLine) {
        return checkoutLine == null ? null : firstText(checkoutLine.id(), checkoutLine.lineId());
    }
}
