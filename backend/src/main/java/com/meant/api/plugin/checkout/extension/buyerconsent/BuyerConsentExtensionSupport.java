package com.meant.api.plugin.checkout.extension.buyerconsent;

import com.meant.api.plugin.checkout.extension.buyerconsent.dto.BuyerConsentState;
import com.meant.api.plugin.spi.CapabilityId;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BuyerConsentExtensionSupport {

    public static final CapabilityId ID = CapabilityId.of("dev.ucp.shopping.buyer_consent");

    private BuyerConsentExtensionSupport() {
    }

    public static boolean active(NegotiatedCapabilities activeCapabilities) {
        return activeCapabilities != null && activeCapabilities.supports(ID);
    }

    public static Map<String, Object> buyer(Map<String, Object> buyer, BuyerConsentState consent) {
        Map<String, Object> values = buyer == null ? new LinkedHashMap<>() : new LinkedHashMap<>(buyer);
        if (consent != null && consent.hasAnyConsentState()) {
            values.put("consent", consent);
        }
        return values.isEmpty() ? null : values;
    }
}
