package com.meant.api.module.user.service;

import com.meant.api.module.user.constant.ShoppingFilterDefaults;
import com.meant.api.module.user.entity.ShoppingFilter;
import com.meant.api.module.user.entity.UserSettings;
import com.meant.api.module.user.entity.UserShoppingFilter;
import com.meant.api.module.user.entity.UserShoppingFilterId;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.repository.ShoppingFilterRepository;
import com.meant.api.module.user.repository.UserSettingsRepository;
import com.meant.api.module.user.repository.UserShoppingFilterRepository;
import com.meant.api.module.user.service.command.UpdateUserSettingsCommand;
import com.meant.api.module.user.service.command.UpsertUserCommand;
import com.meant.api.module.user.service.dto.ShoppingFilterResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class UserSettingsService {

    private final UserService userService;
    private final UserSettingsRepository userSettingsRepository;
    private final UserShoppingFilterRepository userShoppingFilterRepository;
    private final ShoppingFilterRepository shoppingFilterRepository;

    @Transactional
    public UserSettingsResult get(@NotNull @Valid UpsertUserCommand upsertCommand) {
        userService.upsert(upsertCommand);
        Instant now = Instant.now();
        UserSettings settings = findOrCreateSettings(upsertCommand.id(), now);
        return result(settings, List.of(), List.of());
    }

    @Transactional
    public UserSettingsResult update(
            @NotNull @Valid UpsertUserCommand upsertCommand,
            @NotNull @Valid UpdateUserSettingsCommand command
    ) {
        if (!upsertCommand.id().equals(command.id())) {
            throw UserException.forbidden("Settings user does not match authenticated user");
        }
        userService.upsert(upsertCommand);
        Instant now = Instant.now();
        UserSettings settings = findOrCreateSettings(command.id(), now);
        settings.update(command.budget(), command.location(), now);
        List<String> parsedFilterIds = List.copyOf(command.parsedFilterIds());
        replaceFilters(settings, command.filterIds(), parsedFilterIds, now);
        return result(settings, parsedFilterIds, command.unmappedPreferences());
    }

    private UserSettings findOrCreateSettings(UUID userId, Instant now) {
        return userSettingsRepository.findById(userId)
                .orElseGet(() -> {
                    UserSettings settings = userSettingsRepository.save(UserSettings.builder()
                            .userId(userId)
                            .budget(120)
                            .createdAt(now)
                            .updatedAt(now)
                            .build());
                    replaceFilters(settings, Set.copyOf(ShoppingFilterDefaults.DEFAULT_ACTIVE_FILTER_IDS), List.of(), now);
                    return settings;
                });
    }

    private void replaceFilters(
            UserSettings settings,
            Set<String> explicitFilterIds,
            List<String> parsedFilterIds,
            Instant now
    ) {
        if (explicitFilterIds == null && parsedFilterIds.isEmpty()) {
            return;
        }

        LinkedHashSet<String> desiredFilterIds = new LinkedHashSet<>();
        if (explicitFilterIds == null) {
            desiredFilterIds.addAll(activeFilterIds(settings.getUserId()));
        } else {
            desiredFilterIds.addAll(explicitFilterIds);
        }
        desiredFilterIds.addAll(parsedFilterIds);
        validateFilterIds(desiredFilterIds);

        Set<String> currentFilterIds = Set.copyOf(activeFilterIds(settings.getUserId()));
        if (currentFilterIds.equals(desiredFilterIds)) {
            return;
        }

        userShoppingFilterRepository.deleteByIdUserId(settings.getUserId());
        userShoppingFilterRepository.saveAll(desiredFilterIds.stream()
                .map(filterId -> UserShoppingFilter.create(settings.getUserId(), filterId, now))
                .toList());
        settings.touch(now);
    }

    private List<String> activeFilterIds(UUID userId) {
        return userShoppingFilterRepository.findByIdUserId(userId).stream()
                .map(UserShoppingFilter::getId)
                .map(UserShoppingFilterId::getFilterId)
                .toList();
    }

    private void validateFilterIds(Set<String> filterIds) {
        if (filterIds.isEmpty()) {
            return;
        }
        Set<String> existingFilterIds = shoppingFilterRepository.findAllById(filterIds).stream()
                .map(ShoppingFilter::getId)
                .collect(Collectors.toSet());
        List<String> missingFilterIds = filterIds.stream()
                .filter(filterId -> !existingFilterIds.contains(filterId))
                .toList();
        if (!missingFilterIds.isEmpty()) {
            throw new UserException("Unknown shopping filter IDs: " + missingFilterIds);
        }
    }

    private UserSettingsResult result(
            UserSettings settings,
            List<String> parsedFilterIds,
            List<String> unmappedPreferences
    ) {
        List<ShoppingFilter> availableFilters = shoppingFilterRepository.findAllByOrderByDisplayOrderAsc();
        Set<String> activeFilterIds = Set.copyOf(activeFilterIds(settings.getUserId()));
        List<ShoppingFilterResult> activeFilters = availableFilters.stream()
                .filter(filter -> activeFilterIds.contains(filter.getId()))
                .map(ShoppingFilterResult::from)
                .toList();
        return UserSettingsResult.from(
                settings,
                activeFilters,
                availableFilters.stream().map(ShoppingFilterResult::from).toList(),
                parsedFilterIds,
                unmappedPreferences
        );
    }
}
