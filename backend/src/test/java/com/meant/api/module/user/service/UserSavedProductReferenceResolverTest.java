package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.Money;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.OfferAvailability;
import com.meant.api.module.catalog.service.dto.OfferAvailabilityStatus;
import com.meant.api.module.catalog.service.dto.OfferIdentity;
import com.meant.api.module.catalog.service.dto.OfferMerchantScope;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.dto.ResultFreshness;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.module.catalog.service.dto.ResultSourceReference;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.repository.UserProductSearchResultItemRepository;
import com.meant.api.module.user.service.command.SaveUserProductCommand;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserSavedProductReferenceResolverTest {
    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final ProviderIdentity PROVIDER = new ProviderIdentity("SHOPIFY");
    private static final DiscoverySourceIdentity SOURCE = new DiscoverySourceIdentity(
            PROVIDER, ResultSourceType.PROVIDER_CATALOG, "SHOPIFY_GLOBAL");

    @Test
    void resolvesTheExactUserBoundSessionOfferWithoutClientCatalogIdentifiers() {
        UserProductSearchResultItemRepository repository = mock(UserProductSearchResultItemRepository.class);
        UserCanonicalProductSessionStore store =
                new UserCanonicalProductSessionStore(Duration.ofMinutes(30), 100);
        Offer selected = selectedOffer();
        store.rememberOffer(USER_ID, "saved-product", selected);
        UserSavedProductReferenceResolver resolver = new UserSavedProductReferenceResolver(repository, store);

        var resolved = resolver.resolve(command(USER_ID, "saved-product", selected.key()), Instant.now());

        assertThat(resolved.interactionKey()).isEqualTo("saved-product");
        assertThat(resolved.externalProductReference().value()).isEqualTo("product-1");
        assertThat(resolved.externalVariantReference().value()).isEqualTo("variant-large");
        assertThat(resolved.selectedOptions()).containsExactly(
                new ProductAttribute("variant-option", "Size", "L"));
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsAnotherUsersOrAnotherProductsSelectedOffer() {
        UserProductSearchResultItemRepository repository = mock(UserProductSearchResultItemRepository.class);
        UserCanonicalProductSessionStore store =
                new UserCanonicalProductSessionStore(Duration.ofMinutes(30), 100);
        Offer selected = selectedOffer();
        store.rememberOffer(USER_ID, "saved-product", selected);
        UserSavedProductReferenceResolver resolver = new UserSavedProductReferenceResolver(repository, store);

        assertThatThrownBy(() -> resolver.resolve(
                command(OTHER_USER_ID, "saved-product", selected.key()), Instant.now()))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("unknown or expired");
        assertThatThrownBy(() -> resolver.resolve(
                command(USER_ID, "different-product", selected.key()), Instant.now()))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("does not belong");
        verifyNoInteractions(repository);
    }

    private SaveUserProductCommand command(UUID userId, String productKey, String selectedOfferKey) {
        return new SaveUserProductCommand(
                userId,
                productKey,
                "hash",
                "Product",
                "Brand",
                "Category",
                "#fff",
                null,
                null,
                true,
                100,
                25.0d,
                1,
                List.of(),
                List.of(),
                "Note",
                List.of("Pro"),
                List.of(),
                new SaveUserProductCommand.Review(5.0d, 1, "Review"),
                List.of(new SaveUserProductCommand.Offer(
                        "Merchant", 25.0d, "Delivery", null, "merchant.test",
                        "variant-large", "Large", true)),
                null,
                List.of(),
                null,
                selectedOfferKey
        );
    }

    private Offer selectedOffer() {
        ExternalIdentifier merchant = identifier(ExternalIdentifierType.MERCHANT, "merchant-1");
        ExternalIdentifier product = identifier(ExternalIdentifierType.PRODUCT, "product-1");
        ExternalIdentifier variant = identifier(ExternalIdentifierType.VARIANT, "variant-large");
        ResultFreshness freshness = new ResultFreshness(
                Instant.parse("2026-07-14T08:00:00Z"),
                Instant.parse("2026-07-14T08:02:00Z")
        );
        return new Offer(
                new OfferIdentity(
                        PROVIDER,
                        OfferMerchantScope.external(merchant),
                        product,
                        variant,
                        List.of(new ProductAttribute("variant-option", "Size", "L")),
                        List.of(),
                        null
                ),
                "Merchant",
                "Large",
                new Money(2500, "USD"),
                null,
                new OfferAvailability(OfferAvailabilityStatus.IN_STOCK, 1, null),
                List.of(),
                null,
                List.of(new ResultProvenance(
                        PROVIDER,
                        SOURCE,
                        null,
                        merchant,
                        "merchant.test",
                        product,
                        variant,
                        freshness,
                        new ResultSourceReference(ResultSourceType.PROVIDER_CATALOG, SOURCE.value(), null)
                ))
        );
    }

    private ExternalIdentifier identifier(ExternalIdentifierType type, String value) {
        return new ExternalIdentifier(type, PROVIDER.value(), value);
    }
}
