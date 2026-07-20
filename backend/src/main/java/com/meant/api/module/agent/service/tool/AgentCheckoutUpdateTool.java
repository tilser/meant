package com.meant.api.module.agent.service.tool;

import com.meant.api.module.agent.constant.AgentRunEventType;
import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.service.dto.AgentCheckoutResult;
import com.meant.api.module.agent.service.dto.AgentCheckoutToolArguments;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentToolExecutionResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentCheckoutUpdateTool implements AgentTool {

    private static final AgentToolDescriptor DESCRIPTOR = new AgentToolDescriptor(
            "update_checkout",
            "Update buyer identity, shipping address, and discount codes for an owned prepared checkout. This tool cannot provide payment or complete checkout.",
            """
                    {"type":"object","additionalProperties":false,"required":["cartId","buyer","shippingAddress"],"properties":{
                      "cartId":{"type":"string","format":"uuid"},
                      "buyer":{"type":"object","additionalProperties":false,"required":["email","firstName","lastName"],"properties":{"email":{"type":"string","format":"email","maxLength":320},"firstName":{"type":"string","minLength":1,"maxLength":100},"lastName":{"type":"string","minLength":1,"maxLength":100},"phoneNumber":{"type":"string","maxLength":50}}},
                      "shippingAddress":{"type":"object","additionalProperties":false,"required":["streetAddress","addressLocality","postalCode","addressCountry"],"properties":{"streetAddress":{"type":"string","minLength":1,"maxLength":200},"extendedAddress":{"type":"string","maxLength":200},"addressLocality":{"type":"string","minLength":1,"maxLength":120},"addressRegion":{"type":"string","maxLength":120},"postalCode":{"type":"string","minLength":1,"maxLength":30},"addressCountry":{"type":"string","minLength":2,"maxLength":2}}},
                      "discountCodes":{"type":"array","maxItems":20,"items":{"type":"string","minLength":1,"maxLength":100}}
                    }}
                    """,
            "1.0",
            AgentToolRisk.CHECKOUT_PREPARATION
    );

    private final AgentCheckoutToolSupport support;

    @Override
    public AgentToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AgentToolExecutionResult execute(AgentToolExecutionContext context, String argumentsJson) {
        AgentCheckoutToolArguments.Update arguments = support.arguments(
                argumentsJson, AgentCheckoutToolArguments.Update.class);
        AgentCheckoutResult result = AgentCheckoutResult.success(List.of(support.update(context, arguments)));
        String resultJson = support.json(result);
        return new AgentToolExecutionResult(
                resultJson,
                "Updated the prepared checkout.",
                support.artifacts(result),
                AgentRunEventType.CHECKOUT_READY
        );
    }
}
