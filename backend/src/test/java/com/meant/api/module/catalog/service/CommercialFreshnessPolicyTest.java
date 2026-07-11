package com.meant.api.module.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.catalog.service.dto.CommercialFact;
import com.meant.api.module.catalog.service.dto.CommercialFactsFreshness;
import com.meant.api.module.catalog.service.dto.CommercialFreshnessStatus;
import com.meant.api.module.catalog.service.dto.ResultFreshness;
import java.time.Instant;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class CommercialFreshnessPolicyTest {
    private static final Instant NOW = Instant.parse("2026-07-11T00:00:00Z");
    private final CommercialFreshnessPolicy policy = new CommercialFreshnessPolicy();

    @Test
    void blocksEveryStalePurchaseDecisionFact() {
        ResultFreshness stale = new ResultFreshness(NOW.minusSeconds(120), NOW.minusSeconds(1));

        var decision = policy.decide(CommercialFactsFreshness.fromSingleObservation(stale), NOW);

        assertThat(decision.status()).isEqualTo(CommercialFreshnessStatus.REFRESH_REQUIRED);
        assertThat(decision.refreshRequired())
                .containsExactlyInAnyOrderElementsOf(EnumSet.allOf(CommercialFact.class));
    }

    @Test
    void requiresRefreshForMissingOrOpenEndedProviderFreshness() {
        ResultFreshness openEnded = new ResultFreshness(NOW, null);

        var decision = policy.decide(new CommercialFactsFreshness(
                openEnded,
                null,
                openEnded,
                null,
                openEnded
        ), NOW);

        assertThat(decision.status()).isEqualTo(CommercialFreshnessStatus.REFRESH_REQUIRED);
        assertThat(decision.refreshRequired())
                .containsExactlyInAnyOrderElementsOf(EnumSet.allOf(CommercialFact.class));
    }

    @Test
    void acceptsOnlyFactsWithExplicitFutureFreshUntil() {
        ResultFreshness current = new ResultFreshness(NOW, NOW.plusSeconds(30));

        var decision = policy.decide(CommercialFactsFreshness.fromSingleObservation(current), NOW);

        assertThat(decision.status()).isEqualTo(CommercialFreshnessStatus.CURRENT);
        assertThat(decision.refreshRequired()).isEmpty();
    }

    @Test
    void checksOnlyFactsApplicableToTheDownstreamDecision() {
        ResultFreshness current = new ResultFreshness(NOW, NOW.plusSeconds(30));

        var decision = policy.decide(
                new CommercialFactsFreshness(current, current, null, null, null),
                EnumSet.of(CommercialFact.PRICE, CommercialFact.AVAILABILITY),
                NOW
        );

        assertThat(decision.status()).isEqualTo(CommercialFreshnessStatus.CURRENT);
    }
}
