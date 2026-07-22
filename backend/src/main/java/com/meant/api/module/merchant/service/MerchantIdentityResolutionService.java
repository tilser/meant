package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.constant.MerchantIdentityNamespace;
import com.meant.api.module.merchant.constant.MerchantIdentityRole;
import com.meant.api.module.merchant.constant.MerchantRawSource;
import com.meant.api.module.merchant.entity.MerchantRaw;
import com.meant.api.module.merchant.exception.MerchantEnrichmentException;
import com.meant.api.module.merchant.service.dto.MerchantIdentityResolution;
import com.meant.api.module.merchant.service.dto.MerchantIdentityResolutionContext;
import com.meant.api.module.merchant.service.dto.ResolvedMerchantIdentityClaim;
import com.meant.api.module.merchant.service.dto.UcpProfile;
import com.meant.api.module.merchant.service.port.MerchantIdentityResolver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MerchantIdentityResolutionService {

    private final List<MerchantIdentityResolver> resolvers;
    private final MerchantDomainNormalizer domainNormalizer;

    public MerchantIdentityResolution resolve(MerchantRaw source, UcpProfile profile) {
        String sourceDomain = domainNormalizer.normalizeDomain(source.getDomain());
        MerchantIdentityResolutionContext context = new MerchantIdentityResolutionContext(
                sourceDomain,
                source.getObservedProvider(),
                source.getObservedExternalMerchantId(),
                profile
        );
        Optional<MerchantIdentityResolution> resolved = resolvers.stream()
                .map(resolver -> resolver.resolve(context))
                .flatMap(java.util.Optional::stream)
                .findFirst();
        if (resolved.isEmpty() && !fallbackAllowed(source, sourceDomain)) {
            throw new MerchantEnrichmentException(
                    "Merchant source has no verified storefront identity"
            );
        }
        return normalize(resolved.orElseGet(() -> fallback(sourceDomain)));
    }

    private boolean fallbackAllowed(MerchantRaw source, String sourceDomain) {
        return source.getSource() == MerchantRawSource.HUGGING_FACE
                && source.getObservedProvider() == null
                && (source.getObservedExternalMerchantId() == null
                        || source.getObservedExternalMerchantId().isBlank())
                && !sourceDomain.equals("myshopify.com")
                && !sourceDomain.endsWith(".myshopify.com");
    }

    private MerchantIdentityResolution fallback(String sourceDomain) {
        return new MerchantIdentityResolution(
                sourceDomain,
                sourceDomain,
                List.of(new ResolvedMerchantIdentityClaim(
                        MerchantIdentityNamespace.DOMAIN,
                        sourceDomain,
                        MerchantIdentityRole.STOREFRONT_DOMAIN
                ))
        );
    }

    private MerchantIdentityResolution normalize(MerchantIdentityResolution resolution) {
        String canonicalDomain = domainNormalizer.normalizeDomain(resolution.canonicalDomain());
        String name = resolution.merchantName() == null || resolution.merchantName().isBlank()
                ? canonicalDomain
                : resolution.merchantName().trim();

        List<ResolvedMerchantIdentityClaim> claims = new ArrayList<>();
        claims.add(new ResolvedMerchantIdentityClaim(
                MerchantIdentityNamespace.DOMAIN,
                canonicalDomain,
                MerchantIdentityRole.STOREFRONT_DOMAIN
        ));
        claims.addAll(resolution.claims());

        Map<ClaimKey, ResolvedMerchantIdentityClaim> uniqueClaims = new LinkedHashMap<>();
        claims.stream()
                .map(this::normalizeClaim)
                .forEach(claim -> uniqueClaims.putIfAbsent(
                        new ClaimKey(claim.namespace(), claim.normalizedValue()),
                        claim
                ));
        return new MerchantIdentityResolution(
                canonicalDomain,
                name,
                List.copyOf(uniqueClaims.values())
        );
    }

    private ResolvedMerchantIdentityClaim normalizeClaim(ResolvedMerchantIdentityClaim claim) {
        if (claim == null || claim.namespace() == null || claim.role() == null
                || claim.normalizedValue() == null || claim.normalizedValue().isBlank()) {
            throw new MerchantEnrichmentException("Merchant identity claim is incomplete");
        }
        String value = switch (claim.namespace()) {
            case DOMAIN -> domainNormalizer.normalizeDomain(claim.normalizedValue());
            case SHOPIFY_SHOP -> claim.normalizedValue().trim().toLowerCase(java.util.Locale.ROOT);
        };
        return new ResolvedMerchantIdentityClaim(claim.namespace(), value, claim.role());
    }

    private record ClaimKey(MerchantIdentityNamespace namespace, String normalizedValue) {
    }
}
