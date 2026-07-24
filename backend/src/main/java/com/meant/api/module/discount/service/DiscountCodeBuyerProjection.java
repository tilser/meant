package com.meant.api.module.discount.service;

import com.meant.api.module.discount.service.dto.BuyerDiscountCodeResult;
import com.meant.api.module.discount.service.dto.BuyerDiscountCodeSearchResult;
import com.meant.api.module.discount.service.dto.DiscountCodeResult;
import com.meant.api.module.discount.service.dto.DiscountCodeSearchResult;
import com.meant.api.module.merchant.service.MerchantBuyerTextSanitizer;
import com.meant.api.module.merchant.service.MerchantProductMessageSanitizer;
import java.net.URI;
import java.util.List;
import java.util.regex.Pattern;

/** Projects internal discount discovery data onto a buyer-safe response contract. */
public final class DiscountCodeBuyerProjection {

    private static final Pattern SAFE_CODE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    private DiscountCodeBuyerProjection() {
    }

    public static BuyerDiscountCodeSearchResult from(DiscountCodeSearchResult result) {
        String merchantOrigin =
                MerchantBuyerTextSanitizer.buyerSafeMerchantOrigin(result.merchantDomain());
        List<BuyerDiscountCodeResult> codes = result.codes() == null
                ? List.of()
                : result.codes().stream()
                        .map(code -> from(code, merchantOrigin))
                        .filter(code -> code != null)
                        .toList();
        return new BuyerDiscountCodeSearchResult(
                result.merchantId(),
                merchantOrigin,
                result.cached(),
                result.searchedAt(),
                result.expiresAt(),
                codes
        );
    }

    public static BuyerDiscountCodeResult from(DiscountCodeResult result, String merchantOrigin) {
        if (result == null) {
            return null;
        }
        String code = buyerSafeCode(result.code());
        if (code == null) {
            return null;
        }
        String verifiedOrigin =
                MerchantBuyerTextSanitizer.buyerSafeMerchantOrigin(merchantOrigin);
        MerchantProductMessageSanitizer.TransportContext context =
                MerchantProductMessageSanitizer.context(verifiedOrigin, null);
        return new BuyerDiscountCodeResult(
                code,
                MerchantProductMessageSanitizer.sanitizeBuyerText(result.title(), context),
                MerchantProductMessageSanitizer.sanitizeBuyerText(result.description(), context),
                buyerSafeSourceUrl(result.sourceUrl(), verifiedOrigin, context),
                result.confidence(),
                MerchantProductMessageSanitizer.sanitizeBuyerText(result.restrictions(), context),
                result.validUntil(),
                result.expiresAt(),
                MerchantProductMessageSanitizer.sanitizeBuyerText(result.validationMessage(), context)
        );
    }

    private static String buyerSafeSourceUrl(
            String value,
            String merchantOrigin,
            MerchantProductMessageSanitizer.TransportContext context
    ) {
        String safeUrl = MerchantProductMessageSanitizer.buyerSafeUrl(value, context);
        if (safeUrl == null || merchantOrigin == null) {
            return null;
        }
        try {
            URI source = URI.create(safeUrl);
            URI origin = URI.create("https://" + merchantOrigin);
            return source.getHost() != null
                            && origin.getHost() != null
                            && source.getHost().equalsIgnoreCase(origin.getHost())
                            && effectiveHttpsPort(source) == effectiveHttpsPort(origin)
                    ? safeUrl
                    : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static int effectiveHttpsPort(URI value) {
        return value.getPort() < 0 ? 443 : value.getPort();
    }

    private static String buyerSafeCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return SAFE_CODE.matcher(trimmed).matches() ? trimmed : null;
    }
}
