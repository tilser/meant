package com.meant.api.module.agent.service.tool;

import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Structural tool gate. Language and inferred intent never participate in authorization.
 * Checkout preparation is reserved for the explicit user-action lane ({@code runId == null}).
 */
@Service
public class AgentToolAuthorizationPolicy {

    public List<AgentToolDescriptor> available(
            AgentToolExecutionContext context,
            List<AgentToolDescriptor> descriptors
    ) {
        return descriptors.stream()
                .filter(descriptor -> authorized(context, descriptor))
                .toList();
    }

    public boolean authorized(AgentToolExecutionContext context, AgentToolDescriptor descriptor) {
        if (context == null || descriptor == null) {
            return false;
        }
        if (context.runId() == null) {
            return descriptor.riskClass() != AgentToolRisk.IRREVERSIBLE_MUTATION;
        }
        return descriptor.riskClass() == AgentToolRisk.READ
                || descriptor.riskClass() == AgentToolRisk.REVERSIBLE_MUTATION;
    }

    /** Invocation arguments are validated by {@code ReferenceIntegrityPolicy}; this gate remains risk-only. */
    public boolean authorizedInvocation(
            AgentToolExecutionContext context,
            AgentToolDescriptor descriptor,
            String argumentsJson
    ) {
        return authorized(context, descriptor);
    }
}
