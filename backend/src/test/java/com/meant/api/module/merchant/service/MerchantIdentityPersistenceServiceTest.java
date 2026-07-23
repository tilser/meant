package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meant.api.module.merchant.constant.MerchantIdentityNamespace;
import com.meant.api.module.merchant.constant.MerchantIdentityRole;
import com.meant.api.module.merchant.constant.MerchantRawSource;
import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.entity.MerchantIdentity;
import com.meant.api.module.merchant.entity.MerchantRaw;
import com.meant.api.module.merchant.exception.MerchantEnrichmentException;
import com.meant.api.module.merchant.repository.MerchantIdentityRepository;
import com.meant.api.module.merchant.repository.MerchantRepository;
import com.meant.api.module.merchant.service.dto.MerchantIdentityResolution;
import com.meant.api.module.merchant.service.dto.ResolvedMerchantIdentityClaim;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MerchantIdentityPersistenceServiceTest {

    private static final Instant VERIFIED_AT = Instant.parse("2026-07-23T08:00:00Z");

    @Test
    void ownerLookupLoadsAllClaimsOnceAndIgnoresCrossProductMatches() {
        MerchantRepository merchantRepository = mock(MerchantRepository.class);
        MerchantIdentityRepository identityRepository = mock(MerchantIdentityRepository.class);
        Merchant owner = merchant("owner.example");
        Merchant unrelatedOwner = merchant("unrelated.example");
        MerchantIdentityResolution resolution = resolution();
        when(merchantRepository.findByDomain("canonical.example")).thenReturn(Optional.empty());
        when(identityRepository.findByNamespaceInAndNormalizedValueIn(
                Set.of(MerchantIdentityNamespace.DOMAIN, MerchantIdentityNamespace.SHOPIFY_SHOP),
                Set.of("canonical.example", "gid://shopify/shop/1")
        )).thenReturn(List.of(
                identity(
                        unrelatedOwner,
                        MerchantIdentityNamespace.SHOPIFY_SHOP,
                        "canonical.example"
                ),
                identity(
                        owner,
                        MerchantIdentityNamespace.SHOPIFY_SHOP,
                        "gid://shopify/shop/1"
                )
        ));
        MerchantIdentityPersistenceService service =
                new MerchantIdentityPersistenceService(merchantRepository, identityRepository);

        assertThat(service.findOwner(source(), resolution)).contains(owner);

        verify(identityRepository).findByNamespaceInAndNormalizedValueIn(
                Set.of(MerchantIdentityNamespace.DOMAIN, MerchantIdentityNamespace.SHOPIFY_SHOP),
                Set.of("canonical.example", "gid://shopify/shop/1")
        );
    }

    @Test
    void ownerLookupStillRejectsEvidenceOwnedByDifferentMerchants() {
        MerchantRepository merchantRepository = mock(MerchantRepository.class);
        MerchantIdentityRepository identityRepository = mock(MerchantIdentityRepository.class);
        Merchant domainOwner = merchant("canonical.example");
        Merchant claimOwner = merchant("claim.example");
        MerchantIdentityResolution resolution = resolution();
        when(merchantRepository.findByDomain("canonical.example")).thenReturn(Optional.of(domainOwner));
        when(identityRepository.findByNamespaceInAndNormalizedValueIn(any(), any()))
                .thenReturn(List.of(identity(
                        claimOwner,
                        MerchantIdentityNamespace.SHOPIFY_SHOP,
                        "gid://shopify/shop/1"
                )));
        MerchantIdentityPersistenceService service =
                new MerchantIdentityPersistenceService(merchantRepository, identityRepository);

        assertThatThrownBy(() -> service.findOwner(source(), resolution))
                .isInstanceOf(MerchantEnrichmentException.class)
                .hasMessage("Merchant identity evidence belongs to more than one merchant");
    }

    @Test
    void linkingLoadsClaimsOnceAndSavesOnlyDistinctMissingClaimsAsOneBatch() {
        MerchantRepository merchantRepository = mock(MerchantRepository.class);
        MerchantIdentityRepository identityRepository = mock(MerchantIdentityRepository.class);
        Merchant merchant = merchant("canonical.example");
        MerchantRaw source = source();
        ResolvedMerchantIdentityClaim domainClaim = new ResolvedMerchantIdentityClaim(
                MerchantIdentityNamespace.DOMAIN,
                "canonical.example",
                MerchantIdentityRole.STOREFRONT_DOMAIN
        );
        ResolvedMerchantIdentityClaim shopClaim = new ResolvedMerchantIdentityClaim(
                MerchantIdentityNamespace.SHOPIFY_SHOP,
                "gid://shopify/shop/1",
                MerchantIdentityRole.PROVIDER_ID
        );
        MerchantIdentityResolution resolution = new MerchantIdentityResolution(
                "canonical.example",
                "Merchant",
                List.of(domainClaim, shopClaim, shopClaim)
        );
        when(identityRepository.findByNamespaceInAndNormalizedValueIn(any(), any()))
                .thenReturn(List.of(identity(
                        merchant,
                        MerchantIdentityNamespace.DOMAIN,
                        "canonical.example"
                )));
        MerchantIdentityPersistenceService service =
                new MerchantIdentityPersistenceService(merchantRepository, identityRepository);

        service.linkSourceAndPersistClaims(source, merchant, resolution, VERIFIED_AT);

        assertThat(source.getMerchant()).isSameAs(merchant);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<MerchantIdentity>> savedClaims =
                ArgumentCaptor.forClass(Iterable.class);
        verify(identityRepository).saveAll(savedClaims.capture());
        List<MerchantIdentity> saved = new ArrayList<>();
        savedClaims.getValue().forEach(saved::add);
        assertThat(saved).singleElement().satisfies(identity -> {
            assertThat(identity.getMerchant()).isSameAs(merchant);
            assertThat(identity.getNamespace()).isEqualTo(MerchantIdentityNamespace.SHOPIFY_SHOP);
            assertThat(identity.getNormalizedValue()).isEqualTo("gid://shopify/shop/1");
            assertThat(identity.getRole()).isEqualTo(MerchantIdentityRole.PROVIDER_ID);
            assertThat(identity.getSource()).isEqualTo(MerchantRawSource.HUGGING_FACE);
            assertThat(identity.getVerifiedAt()).isEqualTo(VERIFIED_AT);
        });
        verifyNoInteractions(merchantRepository);
    }

    @Test
    void linkingStillRejectsAClaimOwnedByAnotherMerchantBeforeSaving() {
        MerchantRepository merchantRepository = mock(MerchantRepository.class);
        MerchantIdentityRepository identityRepository = mock(MerchantIdentityRepository.class);
        Merchant merchant = merchant("canonical.example");
        Merchant otherMerchant = merchant("other.example");
        MerchantIdentityResolution resolution = resolution();
        when(identityRepository.findByNamespaceInAndNormalizedValueIn(any(), any()))
                .thenReturn(List.of(identity(
                        otherMerchant,
                        MerchantIdentityNamespace.SHOPIFY_SHOP,
                        "gid://shopify/shop/1"
                )));
        MerchantIdentityPersistenceService service =
                new MerchantIdentityPersistenceService(merchantRepository, identityRepository);

        assertThatThrownBy(() ->
                service.linkSourceAndPersistClaims(source(), merchant, resolution, VERIFIED_AT))
                .isInstanceOf(MerchantEnrichmentException.class)
                .hasMessage("Merchant identity claim is already owned");
        verify(identityRepository, never()).saveAll(any());
    }

    private MerchantIdentityResolution resolution() {
        return new MerchantIdentityResolution(
                "canonical.example",
                "Merchant",
                List.of(
                        new ResolvedMerchantIdentityClaim(
                                MerchantIdentityNamespace.DOMAIN,
                                "canonical.example",
                                MerchantIdentityRole.STOREFRONT_DOMAIN
                        ),
                        new ResolvedMerchantIdentityClaim(
                                MerchantIdentityNamespace.SHOPIFY_SHOP,
                                "gid://shopify/shop/1",
                                MerchantIdentityRole.PROVIDER_ID
                        )
                )
        );
    }

    private MerchantIdentity identity(
            Merchant merchant,
            MerchantIdentityNamespace namespace,
            String normalizedValue
    ) {
        return MerchantIdentity.builder()
                .merchant(merchant)
                .namespace(namespace)
                .normalizedValue(normalizedValue)
                .role(MerchantIdentityRole.PROVIDER_ID)
                .source(MerchantRawSource.HUGGING_FACE)
                .verifiedAt(VERIFIED_AT)
                .build();
    }

    private Merchant merchant(String domain) {
        return Merchant.builder()
                .id(UUID.randomUUID())
                .domain(domain)
                .build();
    }

    private MerchantRaw source() {
        return MerchantRaw.builder()
                .source(MerchantRawSource.HUGGING_FACE)
                .domain("source.example")
                .build();
    }
}
