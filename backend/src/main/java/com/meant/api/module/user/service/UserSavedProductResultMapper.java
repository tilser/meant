package com.meant.api.module.user.service;

import com.meant.api.module.user.entity.UserSavedProduct;
import com.meant.api.module.user.service.dto.UserSavedProductResult;
import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.CatalogPurchaseReferencePolicyResolver;
import com.meant.api.module.catalog.service.dto.CatalogProductDetailResult;
import com.meant.api.module.catalog.service.dto.CatalogProductRehydrationResult;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationContext;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationStatus;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.LocalMerchantRouting;
import com.meant.api.module.catalog.service.dto.OfferAvailabilityStatus;
import com.meant.api.module.catalog.service.dto.OfferComponentIdentity;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import com.meant.api.module.catalog.service.dto.ProductMediaType;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.dto.RehydratedCommercialFacts;
import com.meant.api.module.catalog.service.dto.RehydratedProductDetails;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import com.meant.api.module.catalog.service.dto.SellingPlanIdentity;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Maps verified identifiers and ephemeral rehydration facts without reading legacy payload columns. */
@Component
public class UserSavedProductResultMapper {
    private static final TypeReference<List<ProductAttribute>> OPTIONS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<OfferComponentIdentity>> COMPONENTS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final CatalogPurchaseReferencePolicyResolver purchaseReferencePolicyResolver;

    public UserSavedProductResultMapper(
            ObjectMapper objectMapper,
            CatalogPurchaseReferencePolicyResolver purchaseReferencePolicyResolver
    ) {
        this.objectMapper = objectMapper;
        this.purchaseReferencePolicyResolver = purchaseReferencePolicyResolver;
    }

    public CatalogProductReference reference(UserSavedProduct entity) {
        try {
            ProviderIdentity provider = new ProviderIdentity(entity.getSourceProvider());
            return new CatalogProductReference(
                    entity.getProductKey(),
                    new DiscoverySourceIdentity(
                            provider,
                            ResultSourceType.valueOf(entity.getSourceType()),
                            entity.getSourceIdentity()
                    ),
                    entity.getLocalMerchantId(),
                    entity.getMerchantIntegrationId() == null
                            ? null
                            : new LocalMerchantRouting(entity.getMerchantIntegrationId()),
                    ExternalIdentifier.optional(
                            ExternalIdentifierType.MERCHANT,
                            provider.value(),
                            entity.getExternalMerchantId()
                    ),
                    entity.getExternalMerchantDomain(),
                    new ExternalIdentifier(
                            ExternalIdentifierType.PRODUCT,
                            provider.value(),
                            entity.getExternalProductId()
                    ),
                    ExternalIdentifier.optional(
                            ExternalIdentifierType.VARIANT,
                            provider.value(),
                            entity.getExternalVariantId()
                    ),
                    options(entity.getSelectedOptionsJson()),
                    components(entity.getComponentsJson()),
                    sellingPlan(entity.getSellingPlanJson())
            );
        } catch (IllegalArgumentException | JacksonException exception) {
            return null;
        }
    }

    public UserSavedProductResult result(
            UserSavedProduct entity,
            CatalogProductRehydrationResult rehydrated,
            CatalogRehydrationContext context
    ) {
        return result(entity, rehydrated, context, null);
    }

    public UserSavedProductResult detailResult(
            UserSavedProduct entity,
            CatalogProductDetailResult detail,
            CatalogRehydrationContext context
    ) {
        CatalogProductRehydrationResult rehydrated = detail == null ? null : detail.rehydration();
        RehydratedProductDetails details = detail != null
                && rehydrated.status() == CatalogRehydrationStatus.FRESH
                && exactReference(rehydrated)
                ? detail.details()
                : null;
        return result(entity, rehydrated, context, details);
    }

    private UserSavedProductResult result(
            UserSavedProduct entity,
            CatalogProductRehydrationResult rehydrated,
            CatalogRehydrationContext context,
            RehydratedProductDetails details
    ) {
        RehydratedCommercialFacts facts = rehydrated != null
                && rehydrated.status() == CatalogRehydrationStatus.FRESH
                && exactReference(rehydrated)
                ? rehydrated.facts()
                : null;
        MoneyProjection price = facts == null ? null : money(facts.price());
        boolean authoritative = facts != null;
        CatalogProductReference resolved = facts == null ? null : rehydrated.resolvedReference();
        Boolean available = availability(facts);
        List<UserSavedProductResult.Offer> offers = facts == null ? List.of() : List.of(
                new UserSavedProductResult.Offer(
                        cartEligible(resolved, facts)
                                ? SavedProductOfferKeyCodec.encode(entity)
                                : null,
                        merchantDisplayName(resolved, details),
                        price == null ? null : price.majorUnits(),
                        price == null ? null : price.minorUnits(),
                        price == null ? null : price.currency(),
                        null,
                        resolved.localMerchantId() == null ? null : resolved.localMerchantId().toString(),
                        resolved.externalMerchantDomain(),
                        resolved.externalVariantReference() == null
                                ? null
                                : resolved.externalVariantReference().value(),
                        null,
                        available
                )
        );
        String marketCountry = marketCountry(context);
        return new UserSavedProductResult(
                entity.getProductKey(),
                entity.getProductHash(),
                facts == null ? entity.getName() : firstText(facts.title(), entity.getName()),
                entity.getBrand(),
                entity.getCategory(),
                entity.getTone(),
                facts == null ? entity.getImageUrl() : firstText(image(facts), entity.getImageUrl()),
                entity.getProductUrl(),
                entity.getRemote(),
                entity.getMatchScore(),
                price == null ? null : price.majorUnits(),
                price == null ? null : price.minorUnits(),
                price == null ? null : price.currency(),
                entity.getMerchantCount(),
                strings(entity.getSatisfies()),
                strings(entity.getMisses()),
                entity.getNote(),
                strings(entity.getPros()),
                strings(entity.getCons()),
                review(entity),
                offers,
                entity.getNeeds(),
                strings(entity.getProvides()),
                marketCountry,
                marketCountry != null,
                authoritative,
                authoritative ? details : null,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private boolean exactReference(CatalogProductRehydrationResult result) {
        CatalogProductReference requested = result.reference();
        CatalogProductReference resolved = result.resolvedReference();
        RehydratedCommercialFacts facts = result.facts();
        return resolved != null
                && facts != null
                && requested.interactionKey().equals(resolved.interactionKey())
                && requested.discoverySource().equals(resolved.discoverySource())
                && localMerchantMatches(requested, resolved)
                && localRoutingMatches(requested, resolved)
                && externalMerchantMatches(requested, resolved)
                && merchantDomainMatches(requested, resolved)
                && requested.externalProductReference().equals(resolved.externalProductReference())
                && Objects.equals(requested.externalVariantReference(), resolved.externalVariantReference())
                && selectedOptionsMatch(requested, resolved)
                && requested.components().equals(resolved.components())
                && Objects.equals(requested.sellingPlanIdentity(), resolved.sellingPlanIdentity())
                && Objects.equals(resolved.externalVariantReference(), facts.selectedVariant())
                && resolved.selectedOptions().equals(facts.selectedOptions());
    }

    private boolean localMerchantMatches(CatalogProductReference requested, CatalogProductReference resolved) {
        if (Objects.equals(requested.localMerchantId(), resolved.localMerchantId())) {
            return true;
        }
        return requested.localMerchantId() == null
                && requested.localRouting() != null
                && resolved.localMerchantId() != null;
    }

    private boolean localRoutingMatches(CatalogProductReference requested, CatalogProductReference resolved) {
        if (requested.localRouting() != null) {
            return requested.localRouting().equals(resolved.localRouting());
        }
        return resolved.localRouting() == null || requested.localMerchantId() != null;
    }

    private boolean selectedOptionsMatch(CatalogProductReference requested, CatalogProductReference resolved) {
        if (requested.selectedOptions().equals(resolved.selectedOptions())) {
            return true;
        }
        return requested.selectedOptions().isEmpty()
                && requested.externalVariantReference() != null;
    }

    private boolean merchantDomainMatches(CatalogProductReference requested, CatalogProductReference resolved) {
        if (Objects.equals(requested.externalMerchantDomain(), resolved.externalMerchantDomain())) {
            return true;
        }
        return requested.externalMerchantDomain() == null
                && resolved.externalMerchantDomain() != null
                && !resolved.externalMerchantDomain().isBlank();
    }

    private boolean externalMerchantMatches(CatalogProductReference requested, CatalogProductReference resolved) {
        if (Objects.equals(requested.externalMerchantReference(), resolved.externalMerchantReference())) {
            return true;
        }
        return requested.externalMerchantReference() == null
                && resolved.externalMerchantReference() != null
                && (requested.localRouting() != null || requested.localMerchantId() != null);
    }

    private boolean cartEligible(CatalogProductReference reference, RehydratedCommercialFacts facts) {
        if (reference.externalVariantReference() == null
                || (reference.externalMerchantReference() == null && reference.localRouting() == null)) {
            return false;
        }
        if (!purchaseReferencePolicyResolver.allows(reference)) {
            return false;
        }
        return switch (facts.availability().status()) {
            case IN_STOCK, PREORDER, BACKORDER -> true;
            case OUT_OF_STOCK, DISCONTINUED, UNKNOWN -> false;
        };
    }

    private String merchantDisplayName(
            CatalogProductReference reference,
            RehydratedProductDetails details
    ) {
        if (details != null && details.merchantName() != null && !details.merchantName().isBlank()) {
            return details.merchantName().trim();
        }
        String externalMerchant = reference.externalMerchantReference() == null
                ? null
                : reference.externalMerchantReference().value();
        return firstText(
                externalMerchant,
                firstText(reference.discoverySource().value(), reference.discoverySource().provider().value())
        );
    }

    private MoneyProjection money(com.meant.api.module.catalog.service.dto.Money money) {
        if (money == null) {
            return null;
        }
        try {
            Currency currency = Currency.getInstance(money.currency());
            int exponent = currency.getDefaultFractionDigits();
            if (exponent < 0) {
                return null;
            }
            return new MoneyProjection(
                    BigDecimal.valueOf(money.minorUnits(), exponent).doubleValue(),
                    money.minorUnits(),
                    currency.getCurrencyCode()
            );
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String marketCountry(CatalogRehydrationContext context) {
        return context == null || context.country() == null || context.country().isBlank()
                ? null
                : context.country().trim().toUpperCase(java.util.Locale.ROOT);
    }

    private Boolean availability(RehydratedCommercialFacts facts) {
        if (facts == null || facts.availability().status() == OfferAvailabilityStatus.UNKNOWN) {
            return null;
        }
        return facts.availability().status() != OfferAvailabilityStatus.OUT_OF_STOCK
                && facts.availability().status() != OfferAvailabilityStatus.DISCONTINUED;
    }

    private String image(RehydratedCommercialFacts facts) {
        if (facts == null) {
            return null;
        }
        return facts.sourceMedia().stream()
                .filter(media -> media.type() == ProductMediaType.IMAGE)
                .map(media -> media.url().toString())
                .findFirst()
                .orElse(null);
    }

    private UserSavedProductResult.Review review(UserSavedProduct entity) {
        return entity.getReviewScore() == null
                && entity.getReviewCount() == null
                && entity.getReviewInsight() == null
                ? null
                : new UserSavedProductResult.Review(
                        entity.getReviewScore(), entity.getReviewCount(), entity.getReviewInsight());
    }

    private List<String> strings(String value) {
        try {
            List<String> values = objectMapper.readValue(value == null ? "[]" : value, STRING_LIST_TYPE);
            return values == null ? List.of() : List.copyOf(values);
        } catch (JacksonException exception) {
            return List.of();
        }
    }

    private String firstText(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private List<ProductAttribute> options(String value) throws JacksonException {
        List<ProductAttribute> options = objectMapper.readValue(
                value == null || value.isBlank() ? "[]" : value,
                OPTIONS_TYPE
        );
        return options == null ? List.of() : options;
    }

    private List<OfferComponentIdentity> components(String value) throws JacksonException {
        List<OfferComponentIdentity> components = objectMapper.readValue(
                value == null || value.isBlank() ? "[]" : value,
                COMPONENTS_TYPE
        );
        return components == null ? List.of() : components;
    }

    private SellingPlanIdentity sellingPlan(String value) throws JacksonException {
        return value == null || value.isBlank()
                ? null
                : objectMapper.readValue(value, SellingPlanIdentity.class);
    }

    private record MoneyProjection(double majorUnits, long minorUnits, String currency) {
    }
}
