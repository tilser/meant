package com.meant.api.module.user.service;

import com.meant.api.module.user.entity.UserProductSearchResultItem;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.repository.UserProductSearchResultItemRepository;
import com.meant.api.module.user.service.command.SaveUserProductCommand;
import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/** Resolves legacy saves from server-owned admitted cache rows, never from client URLs or prices. */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserSavedProductReferenceResolver {
    private final UserProductSearchResultItemRepository searchResultItemRepository;
    private final UserCanonicalProductSessionStore sessionStore;

    public CatalogProductReference resolve(SaveUserProductCommand command, Instant now) {
        if (command.catalogReference() != null) {
            if (!command.productKey().equals(command.catalogReference().interactionKey())) {
                throw new UserException("Saved product reference did not match the interaction key");
            }
            return sessionReference(command);
        }
        UserProductSearchResultItem item = searchResultItemRepository.findCurrentByUserAndProductKey(
                        command.userId(), command.productKey(), now, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElseThrow(() -> new UserException("Saved product requires a current server-resolved reference"));
        DiscoverySourceIdentity source = item.discoverySource();
        if (source == null) {
            throw new UserException("Saved product source policy is unavailable");
        }
        return new CatalogProductReference(
                command.productKey(),
                source,
                item.getMerchantId(),
                null,
                null,
                new ExternalIdentifier(ExternalIdentifierType.PRODUCT, source.provider().value(), item.getProductId()),
                ExternalIdentifier.optional(
                        ExternalIdentifierType.VARIANT,
                        source.provider().value(),
                        item.getSelectedVariantId()
                ),
                java.util.List.of()
        );
    }

    private CatalogProductReference sessionReference(SaveUserProductCommand command) {
        var product = sessionStore.find(command.userId(), command.productKey())
                .map(UserCanonicalProductSessionStore.Entry::product)
                .orElseThrow(() -> new UserException(
                        "Saved product session is unknown or expired; search for the product again"));
        CatalogProductReference requested = command.catalogReference();
        List<ResolvedCandidate> candidates = product.offers().stream()
                .flatMap(offer -> offer.provenance().stream()
                        .map(provenance -> new ResolvedCandidate(
                                reference(product.key(), offer, provenance), offer)))
                .distinct()
                .toList();
        List<ResolvedCandidate> matches = candidates.stream()
                .filter(candidate -> command.catalogOfferKey() == null
                        || command.catalogOfferKey().isBlank()
                        || command.catalogOfferKey().equals(candidate.offer().key()))
                .filter(candidate -> matches(requested, candidate.reference()))
                .toList();
        if (matches.size() != 1) {
            UserException exception = new UserException(
                    "Saved product reference does not belong to the current product session");
            log.warn(
                    "Saved product session reference rejected candidateCount={} matchingCandidateCount={} "
                            + "sourceMatches={} routingMatches={} merchantMatches={} domainMatches={} "
                            + "productMatches={} variantMatches={} optionMatches={}",
                    candidates.size(),
                    matches.size(),
                    matchCount(candidates, value -> requested.discoverySource().equals(
                            value.reference().discoverySource())),
                    matchCount(candidates, value -> Objects.equals(
                            requested.localRouting(), value.reference().localRouting())),
                    matchCount(candidates, value -> Objects.equals(
                            requested.externalMerchantReference(), value.reference().externalMerchantReference())),
                    matchCount(candidates, value -> requested.externalMerchantDomain() == null
                            || Objects.equals(requested.externalMerchantDomain(),
                                    value.reference().externalMerchantDomain())),
                    matchCount(candidates, value -> requested.externalProductReference().equals(
                            value.reference().externalProductReference())),
                    matchCount(candidates, value -> Objects.equals(
                            requested.externalVariantReference(), value.reference().externalVariantReference())),
                    matchCount(candidates, value -> selectedOptionsMatch(
                            requested.selectedOptions(), value.reference().selectedOptions())),
                    exception
            );
            throw exception;
        }
        return matches.getFirst().reference();
    }

    private CatalogProductReference reference(String productKey, Offer offer, ResultProvenance provenance) {
        return new CatalogProductReference(
                productKey,
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

    private boolean matches(CatalogProductReference requested, CatalogProductReference trusted) {
        return requested.discoverySource().equals(trusted.discoverySource())
                && Objects.equals(requested.localRouting(), trusted.localRouting())
                && Objects.equals(requested.externalMerchantReference(), trusted.externalMerchantReference())
                && (requested.externalMerchantDomain() == null
                        || Objects.equals(requested.externalMerchantDomain(), trusted.externalMerchantDomain()))
                && requested.externalProductReference().equals(trusted.externalProductReference())
                && Objects.equals(requested.externalVariantReference(), trusted.externalVariantReference())
                && selectedOptionsMatch(requested.selectedOptions(), trusted.selectedOptions())
                && requested.components().equals(trusted.components())
                && Objects.equals(requested.sellingPlanIdentity(), trusted.sellingPlanIdentity());
    }

    private boolean selectedOptionsMatch(
            List<ProductAttribute> requested,
            List<ProductAttribute> trusted
    ) {
        if (requested.size() != trusted.size()) {
            return false;
        }
        List<ProductAttribute> unmatched = new ArrayList<>(trusted);
        for (ProductAttribute option : requested) {
            int index = matchingOptionIndex(option, unmatched);
            if (index < 0) {
                return false;
            }
            unmatched.remove(index);
        }
        return unmatched.isEmpty();
    }

    private int matchingOptionIndex(ProductAttribute requested, List<ProductAttribute> trusted) {
        for (int index = 0; index < trusted.size(); index++) {
            ProductAttribute candidate = trusted.get(index);
            if (requested.name().equals(candidate.name())
                    && requested.value().equals(candidate.value())
                    && (requested.group() == null || requested.group().equals(candidate.group()))) {
                return index;
            }
        }
        return -1;
    }

    private long matchCount(
            List<ResolvedCandidate> candidates,
            Predicate<ResolvedCandidate> predicate
    ) {
        return candidates.stream().filter(predicate).count();
    }

    private record ResolvedCandidate(CatalogProductReference reference, Offer offer) {
    }
}
