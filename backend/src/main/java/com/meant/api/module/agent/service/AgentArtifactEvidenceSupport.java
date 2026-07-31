package com.meant.api.module.agent.service;

import com.meant.api.module.agent.entity.AgentArtifactReference;
import java.util.Objects;

final class AgentArtifactEvidenceSupport {

    private AgentArtifactEvidenceSupport() {
    }

    static boolean sameResultSet(AgentArtifactReference left, AgentArtifactReference right) {
        if (left.getMessageId() != null || right.getMessageId() != null) {
            return Objects.equals(left.getMessageId(), right.getMessageId());
        }
        if (left.getToolInvocationId() != null || right.getToolInvocationId() != null) {
            return Objects.equals(left.getToolInvocationId(), right.getToolInvocationId());
        }
        return Objects.equals(left.getRunId(), right.getRunId())
                && Objects.equals(left.getCreatedAt(), right.getCreatedAt());
    }
}
