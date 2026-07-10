package com.meant.api.module.merchant.service.dto;

import com.meant.api.module.merchant.constant.CapabilityAuthorizationStatus;
import java.util.Objects;
import java.util.Set;

public record CapabilityAuthorizationDecision(
        CapabilityAuthorizationStatus status,
        String requiredTier,
        String grantedTier,
        Set<String> requiredScopes,
        Set<String> missingScopes
) {

    public CapabilityAuthorizationDecision {
        Objects.requireNonNull(status, "status must not be null");
        requiredScopes = requiredScopes == null ? Set.of() : Set.copyOf(requiredScopes);
        missingScopes = missingScopes == null ? Set.of() : Set.copyOf(missingScopes);
    }

    public static CapabilityAuthorizationDecision notRequired() {
        return new CapabilityAuthorizationDecision(
                CapabilityAuthorizationStatus.NOT_REQUIRED,
                null,
                null,
                Set.of(),
                Set.of()
        );
    }

    public static CapabilityAuthorizationDecision ready(
            String requiredTier,
            String grantedTier,
            Set<String> requiredScopes
    ) {
        return new CapabilityAuthorizationDecision(
                CapabilityAuthorizationStatus.READY,
                requiredTier,
                grantedTier,
                requiredScopes,
                Set.of()
        );
    }

    public static CapabilityAuthorizationDecision unavailable(CapabilityAuthorizationStatus status) {
        return new CapabilityAuthorizationDecision(status, null, null, Set.of(), Set.of());
    }

    public static CapabilityAuthorizationDecision tierNotGranted(String requiredTier, String grantedTier) {
        return new CapabilityAuthorizationDecision(
                CapabilityAuthorizationStatus.TIER_NOT_GRANTED,
                requiredTier,
                grantedTier,
                Set.of(),
                Set.of()
        );
    }

    public static CapabilityAuthorizationDecision missingScopes(
            String requiredTier,
            String grantedTier,
            Set<String> requiredScopes,
            Set<String> missingScopes
    ) {
        return new CapabilityAuthorizationDecision(
                CapabilityAuthorizationStatus.MISSING_SCOPES,
                requiredTier,
                grantedTier,
                requiredScopes,
                missingScopes
        );
    }

    public boolean ready() {
        return status == CapabilityAuthorizationStatus.READY
                || status == CapabilityAuthorizationStatus.NOT_REQUIRED;
    }
}
