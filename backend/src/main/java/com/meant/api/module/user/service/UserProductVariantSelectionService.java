package com.meant.api.module.user.service;

import com.meant.api.common.util.CountryCodeNormalizer;
import com.meant.api.module.catalog.service.CatalogProductDetailService;
import com.meant.api.module.catalog.service.CatalogPurchaseReferencePolicyResolver;
import com.meant.api.module.catalog.service.dto.CatalogProductDetailResult;
import com.meant.api.module.catalog.service.dto.CatalogProductDetailSelection;
import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationContext;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationStatus;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.Money;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.OfferIdentity;
import com.meant.api.module.catalog.service.dto.OfferMerchantScope;
import com.meant.api.module.catalog.service.dto.OfferRankingEvidence;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.dto.RehydratedCommercialFacts;
import com.meant.api.module.catalog.service.dto.RehydratedProductDetails;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.module.catalog.service.dto.ResultSourceReference;
import com.meant.api.module.catalog.service.support.OfferIdentityStrategy;
import com.meant.api.module.user.exception.SelectedOfferResolutionException;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.SelectUserProductVariantCommand;
import com.meant.api.module.user.service.dto.UserProductVariantSelectionResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.plugin.support.UcpDecimal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

/** Resolves one current option selection against a user-owned live or durable offer anchor. */
@Service
@Validated
@RequiredArgsConstructor
public class UserProductVariantSelectionService {
    private final UserCanonicalProductSessionStore sessionStore;
    private final UserSavedProductOfferResolutionService savedProductOfferResolutionService;
    private final CatalogProductDetailService detailService;
    private final UserSettingsService userSettingsService;
    private final CatalogPurchaseReferencePolicyResolver purchaseReferencePolicyResolver;
    private final List<OfferIdentityStrategy> offerIdentityStrategies;

    public UserProductVariantSelectionResult select(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid SelectUserProductVariantCommand command
    ) {
        if (!profileCommand.id().equals(command.userId())) {
            throw UserException.forbidden("Product variant selection user does not match authenticated user");
        }
        validateOptions(command);
        Anchor anchor = anchor(command.userId(), command.anchorOfferKey());
        List<ProductAttribute> requestedOptions = command.selectedOptions().stream()
                .map(option -> new ProductAttribute("variant-option", option.name(), option.value()))
                .toList();
        CatalogProductDetailResult detail = detailService.getDetails(
                anchor.reference(),
                new CatalogProductDetailSelection(requestedOptions, preferences(command)),
                context(userSettingsService.get(profileCommand))
        );
        if (detail.rehydration().status() != CatalogRehydrationStatus.FRESH || detail.details() == null) {
            throw SelectedOfferResolutionException.rejected(
                    SelectedOfferResolutionException.Failure.PROVIDER_FAILURE,
                    "Current product detail is unavailable for variant selection"
            );
        }

        CatalogProductReference resolvedReference = detail.rehydration().resolvedReference();
        Offer exactOffer = exactOffer(anchor, detail);
        boolean purchaseEligible = exactOffer != null
                && purchaseReferencePolicyResolver.allows(resolvedReference);
        if (purchaseEligible) {
            sessionStore.rememberOffer(command.userId(), anchor.canonicalProductKey(), exactOffer);
        }
        return new UserProductVariantSelectionResult(
                detail.details(),
                purchaseEligible ? exactOffer : null,
                purchaseEligible && cartable(exactOffer)
        );
    }

    private Anchor anchor(UUID userId, String anchorOfferKey) {
        var savedKey = SavedProductOfferKeyCodec.decode(anchorOfferKey);
        if (savedKey.isPresent()) {
            UserSavedProductOfferResolutionService.Selection saved =
                    savedProductOfferResolutionService.selection(userId, savedKey.get(), anchorOfferKey);
            return new Anchor(saved.canonicalProductKey(), null, saved.reference());
        }
        UserCanonicalProductSessionStore.OfferEntry entry = sessionStore.findOffer(userId, anchorOfferKey)
                .orElseThrow(() -> sessionStore.isOfferOwnedByAnotherUser(userId, anchorOfferKey)
                        ? SelectedOfferResolutionException.wrongUser()
                        : SelectedOfferResolutionException.unknownOrExpired());
        Offer offer = entry.offer();
        ResultProvenance provenance = offer.provenance().stream()
                .min(Comparator.comparing(this::sourceKey))
                .orElseThrow(() -> SelectedOfferResolutionException.rejected(
                        SelectedOfferResolutionException.Failure.UNSUPPORTED_SELECTION,
                        "Selected offer has no provider provenance"
                ));
        return new Anchor(entry.canonicalProductKey(), offer, reference(anchorOfferKey, offer, provenance));
    }

    private CatalogProductReference reference(String interactionKey, Offer offer, ResultProvenance provenance) {
        return new CatalogProductReference(
                interactionKey,
                provenance.discoverySource(),
                null,
                provenance.localRouting(),
                provenance.externalMerchantReference(),
                provenance.externalMerchantDomain(),
                provenance.externalProductReference(),
                provenance.externalVariantReference(),
                offer.selectedOptions(),
                offer.identity().components(),
                offer.identity().sellingPlanIdentity()
        );
    }

    private Offer exactOffer(Anchor anchor, CatalogProductDetailResult detail) {
        if (detail.selection() == null || !detail.selection().uniqueCompleteExactMatch()) {
            return null;
        }
        CatalogProductReference resolved = detail.rehydration().resolvedReference();
        RehydratedCommercialFacts facts = detail.rehydration().facts();
        if (resolved == null
                || facts == null
                || resolved.externalVariantReference() == null
                || !Objects.equals(resolved.externalVariantReference(), facts.selectedVariant())
                || !resolved.selectedOptions().equals(facts.selectedOptions())
                || !resolved.selectedOptions().equals(detail.selection().effectiveOptions())) {
            return null;
        }
        OfferMerchantScope merchantScope = merchantScope(resolved);
        if (merchantScope == null) {
            return null;
        }
        ProviderIdentity provider = resolved.discoverySource().provider();
        ExternalIdentifier productIdentity = offerIdentityStrategies.stream()
                .filter(strategy -> strategy.supports(provider))
                .findFirst()
                .map(strategy -> strategy.product(
                        provider,
                        resolved.externalProductReference(),
                        resolved.externalVariantReference()
                ))
                .orElse(resolved.externalProductReference());
        OfferIdentity identity = new OfferIdentity(
                provider,
                merchantScope,
                productIdentity,
                resolved.externalVariantReference(),
                resolved.selectedOptions(),
                resolved.components(),
                resolved.sellingPlanIdentity()
        );
        RehydratedProductDetails.Variant selectedVariant = detail.details().selectedVariant();
        if (selectedVariant != null
                && !resolved.externalVariantReference().value().equals(selectedVariant.variantId())) {
            selectedVariant = null;
        }
        return new Offer(
                identity,
                firstText(facts.merchantName(), detail.details().merchantName(),
                        anchor.offer() == null ? null : anchor.offer().merchantName()),
                firstText(selectedVariant == null ? null : selectedVariant.title(),
                        anchor.offer() == null ? null : anchor.offer().variantTitle()),
                facts.price(),
                listPrice(selectedVariant),
                facts.availability(),
                facts.fulfillment(),
                null,
                anchor.offer() == null ? OfferRankingEvidence.unknown() : anchor.offer().rankingEvidence(),
                List.of(provenance(resolved, facts, anchor.offer()))
        );
    }

    private ResultProvenance provenance(
            CatalogProductReference reference,
            RehydratedCommercialFacts facts,
            Offer anchorOffer
    ) {
        ResultSourceReference sourceReference = anchorOffer == null
                ? null
                : anchorOffer.provenance().stream()
                        .filter(value -> value.discoverySource().equals(reference.discoverySource()))
                        .map(ResultProvenance::sourceReference)
                        .findFirst()
                        .orElse(null);
        if (sourceReference == null) {
            sourceReference = new ResultSourceReference(
                    reference.discoverySource().type(),
                    reference.discoverySource().value(),
                    null
            );
        }
        return new ResultProvenance(
                reference.discoverySource().provider(),
                reference.discoverySource(),
                reference.localRouting(),
                reference.externalMerchantReference(),
                reference.externalMerchantDomain(),
                reference.externalProductReference(),
                reference.externalVariantReference(),
                facts.freshness(),
                sourceReference
        );
    }

    private OfferMerchantScope merchantScope(CatalogProductReference reference) {
        if (reference.externalMerchantReference() != null) {
            return OfferMerchantScope.external(reference.externalMerchantReference());
        }
        return reference.localRouting() == null
                ? null
                : OfferMerchantScope.localIntegrationFallback(reference.localRouting().merchantIntegrationId());
    }

    private boolean cartable(Offer offer) {
        return switch (offer.availability().status()) {
            case IN_STOCK, PREORDER, BACKORDER -> true;
            case OUT_OF_STOCK, DISCONTINUED, UNKNOWN -> false;
        };
    }

    private Money listPrice(RehydratedProductDetails.Variant variant) {
        if (variant == null) {
            return null;
        }
        Long minorUnits = UcpDecimal.decimalAmountToMinor(
                variant.listPriceAmount(), variant.listPriceCurrency());
        return minorUnits == null || variant.listPriceCurrency() == null
                ? null
                : new Money(minorUnits, variant.listPriceCurrency());
    }

    private CatalogRehydrationContext context(UserSettingsResult settings) {
        String country = settings == null || settings.location() == null
                ? null
                : CountryCodeNormalizer.normalizeAlpha2(settings.location().code());
        return new CatalogRehydrationContext(country, null);
    }

    private void validateOptions(SelectUserProductVariantCommand command) {
        Set<String> names = new HashSet<>();
        for (SelectUserProductVariantCommand.SelectedOption option : command.selectedOptions()) {
            String normalized = option.name().toLowerCase(Locale.ROOT);
            if (!names.add(normalized)) {
                throw new UserException("Variant selection contains duplicate option names");
            }
        }
        if (command.preferredOptionName() != null
                && command.selectedOptions().stream().noneMatch(option ->
                        option.name().equalsIgnoreCase(command.preferredOptionName()))) {
            throw new UserException("Preferred option name must identify one requested option");
        }
    }

    private List<String> preferences(SelectUserProductVariantCommand command) {
        if (command.preferredOptionName() == null) {
            return List.of();
        }
        String preferredName = command.selectedOptions().stream()
                .map(SelectUserProductVariantCommand.SelectedOption::name)
                .filter(name -> name.equalsIgnoreCase(command.preferredOptionName()))
                .findFirst()
                .orElse(command.preferredOptionName());
        List<String> preferences = new java.util.ArrayList<>();
        preferences.add(preferredName);
        command.selectedOptions().stream()
                .map(SelectUserProductVariantCommand.SelectedOption::name)
                .filter(name -> !name.equalsIgnoreCase(preferredName))
                .forEach(preferences::add);
        return List.copyOf(preferences);
    }

    private String sourceKey(ResultProvenance provenance) {
        return provenance.discoverySource().provider().value()
                + "\n" + provenance.discoverySource().type().name()
                + "\n" + provenance.discoverySource().value();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private record Anchor(
            String canonicalProductKey,
            Offer offer,
            CatalogProductReference reference
    ) {
    }
}
