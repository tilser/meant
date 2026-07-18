package com.meant.api.module.agent.service.dto;

import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.dto.UserTasteProfileResult;

public record AgentUserPreferencesResult(
        UserSettingsResult settings,
        UserTasteProfileResult tasteProfile
) {
}
