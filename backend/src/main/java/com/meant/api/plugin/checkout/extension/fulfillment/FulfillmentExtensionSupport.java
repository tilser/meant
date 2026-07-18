package com.meant.api.plugin.checkout.extension.fulfillment;

import com.meant.api.plugin.checkout.extension.fulfillment.dto.CheckoutFulfillment;
import com.meant.api.plugin.spi.CapabilityId;
import com.meant.api.plugin.spi.NegotiatedCapabilities;

public final class FulfillmentExtensionSupport {

    public static final CapabilityId ID = CapabilityId.of("dev.ucp.shopping.fulfillment");

    private FulfillmentExtensionSupport() {
    }

    public static boolean active(NegotiatedCapabilities activeCapabilities) {
        return activeCapabilities != null && activeCapabilities.supports(ID);
    }

    public static CheckoutFulfillment fulfillment(CheckoutFulfillment fulfillment) {
        return fulfillment == null || fulfillment.empty() ? null : fulfillment;
    }
}
