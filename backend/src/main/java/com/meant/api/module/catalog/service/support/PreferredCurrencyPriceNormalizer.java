package com.meant.api.module.catalog.service.support;

import com.meant.api.module.catalog.service.dto.CatalogProductDetailResult;
import com.meant.api.module.catalog.service.dto.CatalogProductRehydrationResult;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationContext;
import com.meant.api.module.catalog.service.dto.CommercialFactsFreshness;
import com.meant.api.module.catalog.service.dto.Money;
import com.meant.api.module.catalog.service.dto.OfferDelivery;
import com.meant.api.module.catalog.service.dto.RehydratedCommercialFacts;
import com.meant.api.module.catalog.service.dto.RehydratedProductDetails;
import java.util.List;
import java.util.Locale;

/** Keeps rehydrated prices only when an upstream honored the requested display currency. */
public final class PreferredCurrencyPriceNormalizer {

    private PreferredCurrencyPriceNormalizer() {
    }

    public static CatalogProductRehydrationResult normalize(
            CatalogProductRehydrationResult result,
            CatalogRehydrationContext context
    ) {
        String preferredCurrency = preferredCurrency(context);
        if (preferredCurrency == null || result == null || result.facts() == null) {
            return result;
        }
        RehydratedCommercialFacts facts = result.facts();
        Money price = matching(facts.price(), preferredCurrency) ? facts.price() : null;
        List<OfferDelivery> fulfillment = facts.fulfillment().stream()
                .map(delivery -> normalizedDelivery(delivery, preferredCurrency))
                .toList();
        CommercialFactsFreshness freshness = facts.purchaseFreshness();
        CommercialFactsFreshness normalizedFreshness = new CommercialFactsFreshness(
                price == null ? null : freshness.price(),
                freshness.availability(),
                freshness.selectedVariant(),
                freshness.selectedOptions(),
                freshness.fulfillment()
        );
        RehydratedCommercialFacts normalizedFacts = new RehydratedCommercialFacts(
                facts.title(),
                facts.merchantName(),
                facts.productUrl(),
                price,
                facts.availability(),
                facts.selectedVariant(),
                facts.selectedOptions(),
                fulfillment,
                facts.sourceMedia(),
                facts.freshness(),
                normalizedFreshness
        );
        return CatalogProductRehydrationResult.fresh(
                result.reference(), result.resolvedReference(), normalizedFacts);
    }

    public static CatalogProductDetailResult normalize(
            CatalogProductDetailResult result,
            CatalogRehydrationContext context
    ) {
        String preferredCurrency = preferredCurrency(context);
        if (preferredCurrency == null || result == null || result.details() == null) {
            return result;
        }
        RehydratedProductDetails details = result.details();
        RehydratedProductDetails normalizedDetails = new RehydratedProductDetails(
                details.productId(),
                details.handle(),
                details.title(),
                details.description(),
                details.url(),
                details.imageUrl(),
                details.images(),
                details.media(),
                details.categories(),
                details.tags(),
                details.options(),
                details.selected(),
                details.variants().stream()
                        .map(variant -> normalizedVariant(variant, preferredCurrency))
                        .toList(),
                details.totalVariants(),
                normalizedRange(details.priceRange(), preferredCurrency),
                normalizedRange(details.listPriceRange(), preferredCurrency),
                details.requiresSellingPlan(),
                normalizedVariant(details.selectedVariant(), preferredCurrency),
                details.skus(),
                details.certifications(),
                details.materials(),
                details.collections(),
                details.attributes(),
                details.messages(),
                details.ratingScore(),
                details.ratingScaleMax(),
                details.reviewCount(),
                details.merchantName(),
                details.merchantOrigin(),
                details.technicalEndpointAliases()
        );
        return new CatalogProductDetailResult(
                normalize(result.rehydration(), context),
                normalizedDetails,
                result.selection()
        );
    }

    private static OfferDelivery normalizedDelivery(OfferDelivery delivery, String preferredCurrency) {
        return delivery.cost() == null || matching(delivery.cost(), preferredCurrency)
                ? delivery
                : new OfferDelivery(
                        delivery.method(),
                        delivery.destinationRegion(),
                        delivery.minimumBusinessDays(),
                        delivery.maximumBusinessDays(),
                        null
                );
    }

    private static RehydratedProductDetails.PriceRange normalizedRange(
            RehydratedProductDetails.PriceRange range,
            String preferredCurrency
    ) {
        return range != null && preferredCurrency.equals(normalizedCurrency(range.currency()))
                && (hasText(range.min()) || hasText(range.max()))
                ? new RehydratedProductDetails.PriceRange(range.min(), range.max(), preferredCurrency)
                : null;
    }

    private static RehydratedProductDetails.Variant normalizedVariant(
            RehydratedProductDetails.Variant variant,
            String preferredCurrency
    ) {
        if (variant == null) {
            return null;
        }
        boolean priceMatches = hasText(variant.priceAmount())
                && preferredCurrency.equals(normalizedCurrency(variant.priceCurrency()));
        boolean listPriceMatches = hasText(variant.listPriceAmount())
                && preferredCurrency.equals(normalizedCurrency(variant.listPriceCurrency()));
        return new RehydratedProductDetails.Variant(
                variant.variantId(),
                variant.handle(),
                variant.title(),
                variant.description(),
                variant.url(),
                priceMatches ? variant.priceAmount() : null,
                priceMatches ? preferredCurrency : null,
                listPriceMatches ? variant.listPriceAmount() : null,
                listPriceMatches ? preferredCurrency : null,
                variant.sku(),
                variant.imageUrl(),
                variant.imageAltText(),
                variant.media(),
                variant.available(),
                variant.selectedOptions(),
                variant.categories(),
                variant.tags(),
                variant.attributes()
        );
    }

    private static boolean matching(Money money, String preferredCurrency) {
        return money != null && preferredCurrency.equals(normalizedCurrency(money.currency()));
    }

    private static String preferredCurrency(CatalogRehydrationContext context) {
        return context == null ? null : normalizedCurrency(context.currency());
    }

    private static String normalizedCurrency(String currency) {
        return currency == null || currency.isBlank()
                ? null
                : currency.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
