package com.meant.api.provider.shopify.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.provider.shopify.auth.ShopifyTokenScopeDecision.Availability;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ShopifyTokenScopeDecisionTest {

    @Test
    void filtersNullScopesAndKeepsDeterministicOrder() {
        Set<String> scopes = new HashSet<>();
        scopes.add("zeta:read");
        scopes.add("alpha:read");
        scopes.add(null);

        ShopifyTokenScopeDecision decision = new ShopifyTokenScopeDecision(
                Availability.MISSING_SCOPES,
                scopes,
                scopes,
                Optional.empty()
        );

        assertThat(decision.requiredScopes()).containsExactly("alpha:read", "zeta:read");
        assertThat(decision.missingScopes()).containsExactly("alpha:read", "zeta:read");
    }
}
