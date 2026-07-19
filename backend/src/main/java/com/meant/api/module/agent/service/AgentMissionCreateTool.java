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
public class AgentMissionCreateTool implements AgentTool {

    private static final AgentToolDescriptor DESCRIPTOR = new AgentToolDescriptor(
            "create_shopping_mission",
            "Create a durable shopping mission with a deduplicated checklist, assumptions, constraints, and product alternatives.",
            """
                    {"type":"object","additionalProperties":false,"required":["goal"],"properties":{
                      "goal":{"type":"string","minLength":1,"maxLength":500},
                      "assumptions":{"type":"array","maxItems":20,"items":{"type":"object","additionalProperties":false,"required":["key","value"],"properties":{"key":{"type":"string","maxLength":80},"value":{"type":"string","maxLength":300}}}},
                      "requirements":{"type":"array","maxItems":30,"items":{"type":"object","additionalProperties":false,"required":["id","label"],"properties":{"id":{"type":"string","maxLength":80},"label":{"type":"string","maxLength":200},"requiredQuantity":{"type":"integer","minimum":1,"maximum":1000},"optional":{"type":"boolean"},"searchTerms":{"type":"array","maxItems":8,"items":{"type":"string","maxLength":100}}}}},
                      "constraints":{"type":"object","additionalProperties":false,"properties":{"partySize":{"type":"integer","minimum":1,"maximum":10000},"occasion":{"type":"string","maxLength":120},"dietaryRequirements":{"type":"array","maxItems":12,"items":{"type":"string","maxLength":100}},"neededBy":{"type":"string","format":"date"},"budget":{"type":"object","additionalProperties":false,"required":["amountMinor","currency"],"properties":{"amountMinor":{"type":"integer","minimum":0},"currency":{"type":"string","minLength":3,"maxLength":3}}}}},
                      "alternatives":{"type":"array","maxItems":60,"items":{"type":"object","additionalProperties":false,"required":["requirementId","canonicalProductKey","selected"],"properties":{"requirementId":{"type":"string","maxLength":80},"canonicalProductKey":{"type":"string","minLength":1,"maxLength":200},"offerKey":{"type":"string","maxLength":200},"label":{"type":"string","maxLength":200},"selected":{"type":"boolean"}}}}
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
        AgentMissionToolArguments.Create arguments = support.arguments(
                argumentsJson, AgentMissionToolArguments.Create.class);
        AgentMissionDetails mission = support.create(context, arguments);
        String resultJson = support.toolJson(mission);
        return new AgentToolExecutionResult(
                resultJson,
                "Created shopping mission " + mission.missionId() + ".",
                List.of(support.artifact(mission)),
                null
        );
    }
}
