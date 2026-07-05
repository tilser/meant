package com.meant.api.module.user.service;

import com.meant.api.module.user.constant.ShoppingFilterDefaults;
import com.meant.api.module.user.constant.UserClothingFit;
import com.meant.api.module.user.entity.ShoppingFilter;
import com.meant.api.module.user.entity.UserSettings;
import com.meant.api.module.user.entity.UserSettingsLocation;
import com.meant.api.module.user.entity.UserShoppingFilter;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.repository.ShoppingFilterRepository;
import com.meant.api.module.user.repository.UserSettingsLocationRepository;
import com.meant.api.module.user.repository.UserSettingsRepository;
import com.meant.api.module.user.repository.UserShoppingFilterRepository;
import com.meant.api.module.user.service.command.UpdateUserSettingsCommand;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.UserLocationCommand;
import com.meant.api.module.user.service.dto.ShoppingFilterResult;
import com.meant.api.module.user.service.dto.UserLocationResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    private static final int DEFAULT_BUDGET = 120;

    private final UserService userService;
    private final UserSettingsRepository userSettingsRepository;
    private final UserSettingsLocationRepository userSettingsLocationRepository;
    private final UserShoppingFilterRepository userShoppingFilterRepository;
    private final ShoppingFilterRepository shoppingFilterRepository;

    @Transactional
    public UserSettingsResult get(@NotNull @Valid EnsureUserProfileCommand profileCommand) {
        userService.ensureProfile(profileCommand);
        Instant now = Instant.now();
        UserSettings settings = findOrCreateSettings(profileCommand.id(), now);
        List<String> activeFilterIds = activeFilterIds(settings.getUserId());
        List<ShoppingFilter> availableFilters = shoppingFilterRepository.findAllByOrderByDisplayOrderAsc();
        return result(settings, activeFilterIds, availableFilters, List.of(), List.of());
    }

    @Transactional
    public UserSettingsResult update(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid UpdateUserSettingsCommand command
    ) {
        if (!profileCommand.id().equals(command.id())) {
            throw UserException.forbidden("Settings user does not match authenticated user");
        }
        userService.ensureProfile(profileCommand);
        Instant now = Instant.now();
        UserSettings settings = findOrCreateSettings(command.id(), now);
        if (command.budgetUnlimited()) {
            settings.updateBudget(null, now);
        } else if (command.budget() != null) {
            settings.updateBudget(command.budget(), now);
        }
        if (command.clothingFit() != null) {
            settings.updateClothingFit(UserClothingFit.persistedValue(command.clothingFit()), now);
        }
        if (command.locations() != null) {
            replaceLocations(settings, command.locations(), now);
        } else if (command.location() != null) {
            replaceLocations(settings, List.of(command.location()), now);
        }
        List<String> parsedFilterIds = List.copyOf(command.parsedFilterIds());
        List<String> currentActiveFilterIds = activeFilterIds(settings.getUserId());
        List<ShoppingFilter> availableFilters = shoppingFilterRepository.findAllByOrderByDisplayOrderAsc();
        List<String> activeFilterIds = replaceFilters(
                settings,
                command.filterIds(),
                parsedFilterIds,
                currentActiveFilterIds,
                availableFilters,
                now);
        return result(settings, activeFilterIds, availableFilters, parsedFilterIds, command.unmappedPreferences());
    }

    private UserSettings findOrCreateSettings(UUID userId, Instant now) {
        return userSettingsRepository.findById(userId)
                .orElseGet(() -> {
                    int inserted = userSettingsRepository.insertDefaultIfMissing(userId, DEFAULT_BUDGET, now);
                    if (inserted > 0) {
                        userShoppingFilterRepository.insertIfMissing(
                                userId,
                                ShoppingFilterDefaults.DEFAULT_ACTIVE_FILTER_IDS,
                                now);
                    }
                    return userSettingsRepository.findById(userId)
                            .orElseThrow(() -> UserException.notFound("User settings not found: " + userId));
                });
    }

    private void replaceLocations(
            UserSettings settings,
            List<UserLocationCommand> locations,
            Instant now
    ) {
        List<UserLocationCommand> desiredLocations = normalizedLocations(locations);
        if (sameLocations(storedLocations(settings), desiredLocations)) {
            return;
        }

        userSettingsLocationRepository.deleteByIdUserId(settings.getUserId());
        List<UserSettingsLocation> replacementLocations = new ArrayList<>();
        for (int index = 0; index < desiredLocations.size(); index++) {
            replacementLocations.add(UserSettingsLocation.create(
                    settings.getUserId(),
                    desiredLocations.get(index),
                    index,
                    now));
        }
        userSettingsLocationRepository.saveAll(replacementLocations);
        settings.updatePrimaryLocation(desiredLocations.isEmpty() ? null : desiredLocations.get(0), now);
        settings.touch(now);
    }

    private List<UserLocationResult> storedLocations(UserSettings settings) {
        List<UserLocationResult> locations = userSettingsLocationRepository
                .findByIdUserIdOrderByDisplayOrderAsc(settings.getUserId())
                .stream()
                .map(UserLocationResult::from)
                .toList();
        if (!locations.isEmpty()) {
            return locations;
        }
        UserLocationResult legacyLocation = UserLocationResult.from(settings);
        return legacyLocation == null ? List.of() : List.of(legacyLocation);
    }

    private List<UserLocationCommand> normalizedLocations(List<UserLocationCommand> locations) {
        Map<String, UserLocationCommand> locationsByKey = new LinkedHashMap<>();
        locations.forEach(location -> {
            UserLocationCommand normalized = new UserLocationCommand(
                    location.country().trim(),
                    location.code().trim(),
                    location.city().trim());
            locationsByKey.putIfAbsent(
                    normalized.code().toUpperCase(Locale.ROOT)
                            + "\n"
                            + normalized.city().toLowerCase(Locale.ROOT),
                    normalized);
        });
        return List.copyOf(locationsByKey.values());
    }

    private boolean sameLocations(
            List<UserLocationResult> currentLocations,
            List<UserLocationCommand> desiredLocations
    ) {
        if (currentLocations.size() != desiredLocations.size()) {
            return false;
        }
        for (int index = 0; index < currentLocations.size(); index++) {
            UserLocationResult currentLocation = currentLocations.get(index);
            UserLocationCommand desiredLocation = desiredLocations.get(index);
            if (!currentLocation.country().equals(desiredLocation.country())
                    || !currentLocation.code().equals(desiredLocation.code())
                    || !currentLocation.city().equals(desiredLocation.city())) {
                return false;
            }
        }
        return true;
    }

    private List<String> activeFilterIds(UUID userId) {
        return userShoppingFilterRepository.findFilterIdsByUserId(userId);
    }

    private List<String> replaceFilters(
            UserSettings settings,
            Set<String> explicitFilterIds,
            List<String> parsedFilterIds,
            List<String> currentActiveFilterIds,
            List<ShoppingFilter> availableFilters,
            Instant now
    ) {
        if (explicitFilterIds == null && parsedFilterIds.isEmpty()) {
            return currentActiveFilterIds;
        }

        LinkedHashSet<String> desiredFilterIds = new LinkedHashSet<>();
        if (explicitFilterIds == null) {
            desiredFilterIds.addAll(currentActiveFilterIds);
        } else {
            desiredFilterIds.addAll(explicitFilterIds);
        }
        desiredFilterIds.addAll(parsedFilterIds);
        validateFilterIds(desiredFilterIds, availableFilters);

        Set<String> currentFilterIds = Set.copyOf(currentActiveFilterIds);
        if (currentFilterIds.equals(desiredFilterIds)) {
            return currentActiveFilterIds;
        }

        userShoppingFilterRepository.deleteByIdUserId(settings.getUserId());
        userShoppingFilterRepository.flush();
        userShoppingFilterRepository.saveAll(desiredFilterIds.stream()
                .map(filterId -> UserShoppingFilter.create(settings.getUserId(), filterId, now))
                .toList());
        settings.touch(now);
        return List.copyOf(desiredFilterIds);
    }

    private void validateFilterIds(Set<String> filterIds, List<ShoppingFilter> availableFilters) {
        if (filterIds.isEmpty()) {
            return;
        }
        Set<String> existingFilterIds = availableFilters.stream()
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
            List<String> activeFilterIds,
            List<ShoppingFilter> availableFilters,
            List<String> parsedFilterIds,
            List<String> unmappedPreferences
    ) {
        Set<String> activeFilterIdSet = Set.copyOf(activeFilterIds);
        List<ShoppingFilterResult> activeFilters = availableFilters.stream()
                .filter(filter -> activeFilterIdSet.contains(filter.getId()))
                .map(ShoppingFilterResult::from)
                .toList();
        return UserSettingsResult.from(
                settings,
                storedLocations(settings),
                activeFilters,
                availableFilters.stream().map(ShoppingFilterResult::from).toList(),
                parsedFilterIds,
                unmappedPreferences
        );
    }
}
