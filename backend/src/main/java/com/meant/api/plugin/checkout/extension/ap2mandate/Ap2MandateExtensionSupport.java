package com.meant.api.plugin.checkout.extension.ap2mandate;

import com.meant.api.plugin.checkout.extension.ap2mandate.dto.Ap2CheckoutData;
import com.meant.api.plugin.spi.CapabilityId;
import com.meant.api.plugin.spi.NegotiatedCapabilities;

public final class Ap2MandateExtensionSupport {

    public static final CapabilityId ID = CapabilityId.of("dev.ucp.shopping.ap2_mandate");

    private Ap2MandateExtensionSupport() {
    }

    public static boolean active(NegotiatedCapabilities activeCapabilities) {
        return activeCapabilities != null && activeCapabilities.supports(ID);
    }

    public static Ap2CheckoutData completeRequest(String checkoutMandate) {
        return hasText(checkoutMandate) ? new Ap2CheckoutData(null, checkoutMandate.trim()) : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
