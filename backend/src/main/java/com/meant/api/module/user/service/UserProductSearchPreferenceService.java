package com.meant.api.module.user.service;

import com.meant.api.module.user.constant.UserProductSearchAttributeName;
import com.meant.api.module.user.entity.UserProductSearchPreference;
import com.meant.api.module.user.repository.UserProductSearchPreferenceRepository;
import com.meant.api.module.user.service.command.DeleteUserProductSearchPreferenceCommand;
import com.meant.api.module.user.service.command.SaveUserProductSearchPreferencesCommand;
import com.meant.api.module.user.service.command.UserProductSearchPreferenceCommand;
import com.meant.api.module.user.service.dto.UserProductSearchPreferenceResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@Validated
@RequiredArgsConstructor
public class UserProductSearchPreferenceService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final UserProductSearchPreferenceRepository preferenceRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<UserProductSearchPreferenceResult> list(@NotNull UUID userId) {
        return preferenceRepository.findByUserIdOrderByScopeAscAttributeNameAsc(userId).stream()
                .map(this::result)
                .toList();
    }

    @Transactional
    public void upsert(@NotNull @Valid SaveUserProductSearchPreferencesCommand command) {
        List<UserProductSearchPreferenceCommand> preferences = normalized(command.preferences());
        if (preferences.isEmpty()) {
            return;
        }
        preferenceRepository.lockUserPreferenceWrites(command.userId());
        Instant now = Instant.now();
        preferences.forEach(preference -> upsert(command.userId(), preference, now));
    }

    @Transactional
    public void delete(@NotNull @Valid DeleteUserProductSearchPreferenceCommand command) {
        preferenceRepository.lockUserPreferenceWrites(command.userId());
        String scope = normalizeScope(command.scope());
        if (scope == null) {
            return;
        }
        preferenceRepository.deleteByUserIdAndScopeAndAttributeName(
                command.userId(), scope, command.attributeName());
    }

    private void upsert(UUID userId, UserProductSearchPreferenceCommand preference, Instant now) {
        UserProductSearchPreference stored = preferenceRepository.findByUserIdAndScopeAndAttributeName(
                        userId, preference.scope(), preference.attributeName())
                .orElseGet(() -> UserProductSearchPreference.create(
                        userId,
                        preference.scope(),
                        preference.attributeName(),
                        encode(preference.values()),
                        now
                ));
        stored.replaceValues(encode(preference.values()), now);
        preferenceRepository.save(stored);
    }

    private List<UserProductSearchPreferenceCommand> normalized(
            List<UserProductSearchPreferenceCommand> preferences
    ) {
        Map<String, UserProductSearchPreferenceCommand> byKey = new LinkedHashMap<>();
        for (UserProductSearchPreferenceCommand preference : preferences) {
            if (preference.attributeName() != UserProductSearchAttributeName.SIZE) {
                continue;
            }
            String scope = normalizeScope(preference.scope());
            List<String> values = new LinkedHashSet<>(preference.values().stream()
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .toList()).stream().limit(10).toList();
            if (scope == null || values.isEmpty()) {
                continue;
            }
            UserProductSearchPreferenceCommand normalized = new UserProductSearchPreferenceCommand(
                    scope, preference.attributeName(), values);
            byKey.put(scope + "\n" + preference.attributeName().name(), normalized);
        }
        return List.copyOf(byKey.values());
    }

    private String normalizeScope(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (normalized.isBlank()) {
            return null;
        }
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 80).replaceAll("-+$", "");
    }

    private UserProductSearchPreferenceResult result(UserProductSearchPreference preference) {
        return new UserProductSearchPreferenceResult(
                preference.getScope(),
                preference.getAttributeName(),
                decode(preference.getValuesJson())
        );
    }

    private String encode(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize product-search preference", exception);
        }
    }

    private List<String> decode(String value) {
        try {
            return List.copyOf(objectMapper.readValue(value, STRING_LIST));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not deserialize product-search preference", exception);
        }
    }
}
