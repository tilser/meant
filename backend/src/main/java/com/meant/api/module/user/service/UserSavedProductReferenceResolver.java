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
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/** Resolves legacy saves from server-owned admitted cache rows, never from client URLs or prices. */
@Service
@RequiredArgsConstructor
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
        List<CatalogProductReference> matches = product.offers().stream()
                .flatMap(offer -> offer.provenance().stream()
                        .map(provenance -> reference(product.key(), offer, provenance)))
                .filter(candidate -> matches(requested, candidate))
                .distinct()
                .toList();
        if (matches.size() != 1) {
            throw new UserException("Saved product reference does not belong to the current product session");
        }
        return matches.getFirst();
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
                offer.selectedOptions()
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
                && requested.selectedOptions().equals(trusted.selectedOptions());
    }
}
