package com.meant.api.module.agent.service;

import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.service.dto.AgentMissionDetails;
import com.meant.api.module.agent.service.dto.AgentMissionToolArguments;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentToolExecutionResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentMissionEvaluateCoverageTool implements AgentTool {

    private static final AgentToolDescriptor DESCRIPTOR = new AgentToolDescriptor(
            "evaluate_mission_coverage",
            "Deterministically evaluate how exact product, offer, or owned-inventory references cover an owned mission checklist.",
            """
                    {"type":"object","additionalProperties":false,"required":["missionId"],"properties":{
                      "missionId":{"type":"string","format":"uuid"},
                      "selections":{"type":"array","maxItems":100,"items":{"type":"object","additionalProperties":false,"required":["requirementId"],"anyOf":[{"required":["canonicalProductKey"]},{"required":["inventoryItemId"]}],"properties":{"requirementId":{"type":"string","maxLength":80},"canonicalProductKey":{"type":"string","minLength":1,"maxLength":200},"offerKey":{"type":"string","maxLength":200},"inventoryItemId":{"type":"string","format":"uuid"},"quantity":{"type":"integer","minimum":1,"maximum":1000}}}}
                    }}
                    """,
            "1.0",
            AgentToolRisk.REVERSIBLE_MUTATION
    );

    private final AgentMissionToolSupport support;

    @Override
    public AgentToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AgentToolExecutionResult execute(AgentToolExecutionContext context, String argumentsJson) {
        AgentMissionToolArguments.EvaluateCoverage arguments = support.arguments(
                argumentsJson, AgentMissionToolArguments.EvaluateCoverage.class);
        AgentMissionDetails mission = support.evaluateCoverage(context, arguments);
        String resultJson = support.toolJson(mission);
        return new AgentToolExecutionResult(
                resultJson,
                "Evaluated coverage for shopping mission " + mission.missionId() + ".",
                List.of(support.artifact(mission, resultJson)),
                null
        );
    }
}
