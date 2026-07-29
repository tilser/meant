package com.meant.api.module.agent.service.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Server-resolved link from a new conversation turn to one persisted search qualification. */
public record AgentPendingProductSearchContinuation(
        UUID qualificationId,
        String originalQuery,
        Instant observedUpdatedAt
) {

    public AgentPendingProductSearchContinuation {
        qualificationId = Objects.requireNonNull(
                qualificationId,
                "A pending product-search continuation requires a qualification ID"
        );
        originalQuery = Objects.requireNonNull(
                originalQuery,
                "A pending product-search continuation requires its original query"
        ).trim();
        observedUpdatedAt = Objects.requireNonNull(
                observedUpdatedAt,
                "A pending product-search continuation requires its observed revision"
        );
        if (originalQuery.isEmpty()) {
            throw new IllegalArgumentException(
                    "A pending product-search continuation requires a non-blank original query"
            );
        }
    }
}
