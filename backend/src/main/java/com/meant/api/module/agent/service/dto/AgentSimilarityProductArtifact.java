package com.meant.api.module.agent.service.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * Durable product payload for similarity results.
 *
 * <p>The anchor is stored with the product artifact so the UI can still render grounded copy when
 * the model-facing tool result is replaced by a bounded preview.
 */
public record AgentSimilarityProductArtifact(
        @JsonUnwrapped AgentCanonicalProductArtifact product,
        AgentSimilarityAnchorResult similarityAnchor
) {
}
