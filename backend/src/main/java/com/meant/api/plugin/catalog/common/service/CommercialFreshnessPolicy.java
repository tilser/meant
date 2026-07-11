package com.meant.api.plugin.catalog.common.service;

import com.meant.api.plugin.catalog.common.dto.CommercialFact;
import com.meant.api.plugin.catalog.common.dto.CommercialFactsFreshness;
import com.meant.api.plugin.catalog.common.dto.CommercialFreshnessDecision;
import com.meant.api.plugin.catalog.common.dto.CommercialFreshnessStatus;
import com.meant.api.plugin.catalog.common.dto.ResultFreshness;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Purchase-decision boundary: absent, open-ended, or expired observations must be refreshed. */
@Component
public class CommercialFreshnessPolicy {
    public CommercialFreshnessDecision decide(CommercialFactsFreshness facts, Instant now) {
        return decide(facts, EnumSet.allOf(CommercialFact.class), now);
    }

    public CommercialFreshnessDecision decide(
            CommercialFactsFreshness facts,
            Set<CommercialFact> requiredFacts,
            Instant now
    ) {
        EnumSet<CommercialFact> refresh = EnumSet.noneOf(CommercialFact.class);
        Set<CommercialFact> required = requiredFacts == null ? Set.of() : Set.copyOf(requiredFacts);
        requireCurrent(required, refresh, CommercialFact.PRICE, facts == null ? null : facts.price(), now);
        requireCurrent(required, refresh, CommercialFact.AVAILABILITY, facts == null ? null : facts.availability(), now);
        requireCurrent(required, refresh, CommercialFact.SELECTED_VARIANT,
                facts == null ? null : facts.selectedVariant(), now);
        requireCurrent(required, refresh, CommercialFact.SELECTED_OPTIONS,
                facts == null ? null : facts.selectedOptions(), now);
        requireCurrent(required, refresh, CommercialFact.FULFILLMENT, facts == null ? null : facts.fulfillment(), now);
        return new CommercialFreshnessDecision(
                refresh.isEmpty() ? CommercialFreshnessStatus.CURRENT : CommercialFreshnessStatus.REFRESH_REQUIRED,
                refresh
        );
    }

    private void requireCurrent(
            Set<CommercialFact> required,
            EnumSet<CommercialFact> refresh,
            CommercialFact fact,
            ResultFreshness freshness,
            Instant now
    ) {
        if (required.contains(fact)
                && (freshness == null || freshness.freshUntil() == null || !freshness.freshUntil().isAfter(now))) {
            refresh.add(fact);
        }
    }
}
