package com.meant.api.plugin.checkout.extension.buyerconsent;

import com.meant.api.plugin.checkout.common.dto.CheckoutBuyer;
import com.meant.api.plugin.checkout.extension.buyerconsent.dto.BuyerConsentState;
import com.meant.api.plugin.checkout.extension.buyerconsent.dto.BuyerWithConsent;
import com.meant.api.plugin.spi.CapabilityId;
import com.meant.api.plugin.spi.NegotiatedCapabilities;

public final class BuyerConsentExtensionSupport {

    public static final CapabilityId ID = CapabilityId.of("dev.ucp.shopping.buyer_consent");

    private BuyerConsentExtensionSupport() {
    }

    public static boolean active(NegotiatedCapabilities activeCapabilities) {
        return activeCapabilities != null && activeCapabilities.supports(ID);
    }

    public static BuyerWithConsent buyer(CheckoutBuyer buyer, BuyerConsentState consent) {
        return BuyerWithConsent.from(buyer, consent);
    }
}
