package com.meant.api.module.user.service;

import com.meant.api.module.user.entity.UserProductSearchResultItem;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.repository.UserProductSearchResultItemRepository;
import com.meant.api.module.user.service.command.SaveUserProductCommand;
import com.meant.api.plugin.catalog.common.dto.CatalogProductReference;
import com.meant.api.plugin.catalog.common.dto.DiscoverySourceIdentity;
import com.meant.api.plugin.catalog.common.dto.ExternalIdentifier;
import com.meant.api.plugin.catalog.common.dto.ExternalIdentifierType;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/** Resolves legacy saves from server-owned admitted cache rows, never from client URLs or prices. */
@Service
@RequiredArgsConstructor
public class UserSavedProductReferenceResolver {
    private final UserProductSearchResultItemRepository searchResultItemRepository;

    public CatalogProductReference resolve(SaveUserProductCommand command, Instant now) {
        if (command.catalogReference() != null) {
            if (!command.productKey().equals(command.catalogReference().interactionKey())) {
                throw new UserException("Saved product reference did not match the interaction key");
            }
            return command.catalogReference();
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
                ExternalIdentifier.optional(
                        ExternalIdentifierType.MERCHANT,
                        source.provider().value(),
                        item.getMerchantDomain()
                ),
                new ExternalIdentifier(ExternalIdentifierType.PRODUCT, source.provider().value(), item.getProductId()),
                ExternalIdentifier.optional(
                        ExternalIdentifierType.VARIANT,
                        source.provider().value(),
                        item.getSelectedVariantId()
                ),
                java.util.List.of()
        );
    }
}
