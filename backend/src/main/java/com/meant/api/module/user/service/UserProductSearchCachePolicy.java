package com.meant.api.module.user.service;

import com.meant.api.module.user.entity.UserProductSearch;
import com.meant.api.module.user.entity.UserProductSearchResultItem;
import com.meant.api.module.user.service.dto.UserProductSearchProductSnapshot;
import com.meant.api.module.catalog.service.dto.CatalogSearchRetentionAdmission;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.CatalogDataUsePolicyResolver;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;

/** Source-aware admission and fingerprint validation for the legacy user search cache. */
@Component
public class UserProductSearchCachePolicy {
    private final CatalogDataUsePolicyResolver policyResolver;

    public UserProductSearchCachePolicy(CatalogDataUsePolicyResolver policyResolver) {
        this.policyResolver = policyResolver;
    }

    public WriteDecision decide(
            List<UserProductSearchProductSnapshot> products,
            Collection<DiscoverySourceIdentity> declaredSources,
            Instant now,
            Instant requestedExpiresAt
    ) {
        List<DiscoverySourceIdentity> observedSources = new ArrayList<>();
        if (declaredSources != null) {
            observedSources.addAll(declaredSources);
        }
        products.stream().map(UserProductSearchProductSnapshot::discoverySource).forEach(observedSources::add);
        CatalogSearchRetentionAdmission admission = policyResolver.admitSearch(
                observedSources.stream().distinct().toList()
        );
        if (!admission.admitted()) {
            return WriteDecision.sessionOnly();
        }
        Instant policyExpiresAt = now.plus(admission.maximumRetention());
        return new WriteDecision(
                true,
                requestedExpiresAt.isBefore(policyExpiresAt) ? requestedExpiresAt : policyExpiresAt,
                admission.policyFingerprint()
        );
    }

    public boolean isCurrent(UserProductSearch search, List<UserProductSearchResultItem> items) {
        if (search.getRetentionPolicyFingerprint() == null) {
            return false;
        }
        if (items.isEmpty()) {
            return true;
        }
        CatalogSearchRetentionAdmission admission = policyResolver.admitSearch(items.stream()
                .map(UserProductSearchResultItem::discoverySource)
                .distinct()
                .toList());
        return admission.admitted()
                && search.getRetentionPolicyFingerprint().equals(admission.policyFingerprint());
    }

    public record WriteDecision(boolean persist, Instant expiresAt, String policyFingerprint) {
        private static WriteDecision sessionOnly() {
            return new WriteDecision(false, null, null);
        }
    }
}
