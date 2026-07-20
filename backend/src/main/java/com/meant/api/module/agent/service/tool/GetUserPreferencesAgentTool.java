package com.meant.api.module.agent.service.tool;

import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.service.AgentContextProfileService;
import com.meant.api.module.agent.service.AgentJsonSupport;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentToolExecutionResult;
import com.meant.api.module.agent.service.dto.AgentUserPreferencesResult;
import com.meant.api.module.agent.service.dto.GetUserPreferencesAgentToolInput;
import com.meant.api.module.user.service.UserSettingsService;
import com.meant.api.module.user.service.UserTasteProfileService;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetUserPreferencesAgentTool implements AgentTool {

    private static final AgentToolDescriptor DESCRIPTOR = new AgentToolDescriptor(
            "get_user_preferences",
            "Read the current user's shopping settings, active filters, locations, and learned taste signals.",
            """
            {"type":"object","properties":{},"additionalProperties":false}
            """,
            "1",
            AgentToolRisk.READ
    );

    private final AgentJsonSupport json;
    private final AgentContextProfileService profileService;
    private final UserSettingsService userSettingsService;
    private final UserTasteProfileService userTasteProfileService;

    @Override
    public AgentToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AgentToolExecutionResult execute(AgentToolExecutionContext context, String argumentsJson) {
        json.readArguments(argumentsJson, GetUserPreferencesAgentToolInput.class);
        var profile = profileService.profile(context.userId());
        UserSettingsResult settings = userSettingsService.get(profile);
        var taste = userTasteProfileService.profile(context.userId(), settings);
        return AgentToolExecutionResult.read(
                json.write(new AgentUserPreferencesResult(settings, taste)),
                "Loaded the user's current shopping preferences.",
                List.of()
        );
    }
}
