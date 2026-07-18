package com.meant.api.provider.shopify.catalog;

import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.CatalogSimilarityReference;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.ProductIdentityEvidenceKind;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import com.meant.api.module.catalog.service.port.CatalogSimilarityReferenceResolver;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Resolves server-owned Shopify product GIDs for Global Catalog similarity search. */
@Component
public class ShopifyCatalogSimilarityReferenceResolver implements CatalogSimilarityReferenceResolver {

    private static final Pattern PRODUCT_GID = Pattern.compile(
            "^gid://shopify/(?:p|Product)/[^/?#\\s]+$");

    @Override
    public Optional<CatalogSimilarityReference> resolve(CanonicalProduct product) {
        if (product == null) {
            return Optional.empty();
        }
        Optional<CatalogSimilarityReference> upid = product.identityEvidence().stream()
                .filter(evidence -> evidence.kind() == ProductIdentityEvidenceKind.UPID)
                .flatMap(evidence -> evidence.identifiers().stream())
                .map(this::reference)
                .flatMap(Optional::stream)
                .findFirst();
        if (upid.isPresent()) {
            return upid;
        }
        Optional<CatalogSimilarityReference> providerCatalog = product.provenance().stream()
                .filter(provenance -> provenance.discoverySource().type() == ResultSourceType.PROVIDER_CATALOG)
                .map(ResultProvenance::externalProductReference)
                .map(this::reference)
                .flatMap(Optional::stream)
                .findFirst();
        return providerCatalog.or(() -> product.provenance().stream()
                .map(ResultProvenance::externalProductReference)
                .map(this::reference)
                .flatMap(Optional::stream)
                .findFirst());
    }

    private Optional<CatalogSimilarityReference> reference(ExternalIdentifier identifier) {
        if (identifier == null || identifier.value() == null
                || !PRODUCT_GID.matcher(identifier.value()).matches()) {
            return Optional.empty();
        }
        return Optional.of(new CatalogSimilarityReference(
                ShopifyOfferIdentityStrategy.PROVIDER,
                new ExternalIdentifier(
                        ExternalIdentifierType.PRODUCT,
                        ShopifyOfferIdentityStrategy.PROVIDER.value(),
                        identifier.value()
                )
        ));
    }
}
