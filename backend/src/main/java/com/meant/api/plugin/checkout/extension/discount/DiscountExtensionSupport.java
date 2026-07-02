package com.meant.api.plugin.checkout.extension.discount;

import com.meant.api.plugin.checkout.extension.discount.dto.CheckoutDiscounts;
import com.meant.api.plugin.spi.CapabilityId;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import java.util.List;

public final class DiscountExtensionSupport {

    public static final CapabilityId ID = CapabilityId.of("dev.ucp.shopping.discount");

    private DiscountExtensionSupport() {
    }

    public static boolean active(NegotiatedCapabilities activeCapabilities) {
        return activeCapabilities != null && activeCapabilities.supports(ID);
    }

    public static CheckoutDiscounts discountCodes(List<String> discountCodes) {
        List<String> codes = discountCodes == null
                ? List.of()
                : discountCodes.stream()
                        .filter(DiscountExtensionSupport::hasText)
                        .map(String::trim)
                        .toList();
        CheckoutDiscounts discounts = CheckoutDiscounts.codes(codes);
        return discounts.empty() ? null : discounts;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
