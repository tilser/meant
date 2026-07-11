package com.meant.api.module.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.catalog.service.dto.ProductGroupingDecision;
import com.meant.api.module.catalog.service.dto.ProductGroupingDecisionOutcome;
import com.meant.api.module.catalog.service.dto.ProductGroupingDecisionReason;
import com.meant.api.module.catalog.service.dto.ProductIdentityContradictionKind;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductGroupingMetricsTest {

    @Test
    void recordsOnlyControlledOutcomeReasonAndContradictionTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ProductGroupingMetrics metrics = new ProductGroupingMetrics(registry);
        metrics.record(List.of(new ProductGroupingDecision(
                "offer-a",
                "offer-b",
                ProductGroupingDecisionOutcome.SEPARATE,
                ProductGroupingDecisionReason.CONTRADICTION_VETO,
                10_000,
                List.of(),
                List.of(ProductIdentityContradictionKind.COLOR, ProductIdentityContradictionKind.SIZE)
        )));

        assertThat(registry.get("commerce.catalog.grouping.decisions")
                .tags("outcome", "separate", "reason", "contradiction_veto")
                .counter().count()).isEqualTo(1d);
        assertThat(registry.get("commerce.catalog.grouping.contradictions")
                .tag("contradiction", "color").counter().count()).isEqualTo(1d);
        assertThat(registry.get("commerce.catalog.grouping.contradictions")
                .tag("contradiction", "size").counter().count()).isEqualTo(1d);
        assertThat(registry.getMeters()).allSatisfy(meter ->
                assertThat(meter.getId().getTags()).allSatisfy(tag ->
                        assertThat(tag.getKey()).isIn("outcome", "reason", "contradiction")));
    }
}
