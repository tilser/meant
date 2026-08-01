package com.meant.api.module.agent.service;

import com.meant.api.module.user.service.UserService;
import com.meant.api.module.user.service.UserSettingsService;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.dto.ShoppingFilterResult;
import com.meant.api.module.user.service.query.GetUserActiveShoppingFiltersQuery;
import com.meant.api.module.user.service.query.GetUserQuery;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgentContextProfileService {

    private final UserService userService;
    private final UserSettingsService userSettingsService;

    public EnsureUserProfileCommand profile(UUID userId) {
        var user = userService.get(new GetUserQuery(userId));
        return new EnsureUserProfileCommand(
                user.getId(), user.getEmail(), user.getFirstName(), user.getSurname());
    }

    public List<ShoppingFilterResult> activePreferences(UUID userId) {
        return userSettingsService.activeFilters(new GetUserActiveShoppingFiltersQuery(userId));
    }
}
