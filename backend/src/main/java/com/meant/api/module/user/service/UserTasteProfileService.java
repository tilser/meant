package com.meant.api.module.user.service;

import static com.meant.api.common.util.CollectionUtils.safeList;

import com.meant.api.module.user.constant.UserTasteBehaviorType;
import com.meant.api.module.user.constant.UserTasteSignalStatus;
import com.meant.api.module.user.constant.UserTasteSignalType;
import com.meant.api.module.user.constant.UserTasteSuggestionStatus;
import com.meant.api.module.user.entity.ShoppingFilter;
import com.meant.api.module.user.entity.UserTasteSignal;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.repository.ShoppingFilterRepository;
import com.meant.api.module.user.repository.UserTasteSignalRepository;
import com.meant.api.module.user.service.command.AcceptUserTasteSuggestionCommand;
import com.meant.api.module.user.service.command.RecordUserTasteBehaviorCommand;
import com.meant.api.module.user.service.command.SaveUserProductCommand;
import com.meant.api.module.user.service.command.UpdateUserSettingsCommand;
import com.meant.api.module.user.service.command.UpdateUserTasteSignalCommand;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.dto.ShoppingFilterResult;
import com.meant.api.module.user.service.dto.UserProductSearchQueryIntentResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.dto.UserTasteProfileResult;
import com.meant.api.module.user.service.dto.UserTasteSignalResult;
import com.meant.api.module.user.service.dto.UserTasteSuggestionResult;
import com.meant.api.module.user.service.query.GetUserTasteProfileQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class UserTasteProfileService {

    private static final Pattern SPACE_PATTERN = Pattern.compile("\\s+");
    private static final double SAVE_WEIGHT = 1.4d;
    private static final double PURCHASE_WEIGHT = 2.4d;
    private static final double DISMISS_WEIGHT = -1.8d;
    private static final double SEARCH_WEIGHT = 0.35d;
    private static final double SUGGESTION_THRESHOLD = 2.5d;
    private static final int RANKING_SIGNAL_LIMIT = 30;

    private final UserService userService;
    private final UserSettingsService userSettingsService;
    private final UserTasteSignalRepository userTasteSignalRepository;
    private final ShoppingFilterRepository shoppingFilterRepository;

    private record TasteSignalMutation(
            UserTasteSignalType signalType,
            String signalKey,
            String label,
            String behavior,
            double weight,
            String suggestedFilterId
    ) {
    }

    private record TasteSignalKey(UserTasteSignalType signalType, String signalKey) {
    }

    @Transactional
    public UserTasteProfileResult get(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid GetUserTasteProfileQuery query
    ) {
        validateUser(profileCommand, query.userId(), "Taste profile user does not match authenticated user");
        userService.ensureProfile(profileCommand);
        UserSettingsResult settings = userSettingsService.get(profileCommand);
        return profile(query.userId(), settings);
    }

    @Transactional
    public UserTasteProfileResult recordBehavior(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid RecordUserTasteBehaviorCommand command
    ) {
        validateUser(profileCommand, command.userId(), "Taste behavior user does not match authenticated user");
        validateUser(profileCommand, command.product().userId(), "Taste behavior product user does not match authenticated user");
        userService.ensureProfile(profileCommand);
        recordProductSignals(command.userId(), command.behavior(), command.product(), Instant.now());
        return profile(command.userId(), userSettingsService.get(profileCommand));
    }

    @Transactional
    public void recordSavedProduct(UUID userId, SaveUserProductCommand command, Instant now) {
        recordProductSignals(userId, UserTasteBehaviorType.SAVE, command, now);
    }

    @Transactional
    public void recordSearch(UUID userId, UserProductSearchQueryIntentResult queryIntent, Instant now) {
        if (queryIntent == null) {
            return;
        }
        String signal = normalized(queryIntent.searchQuery());
        if (signal == null || signal.length() < 3) {
            return;
        }
        String label = queryIntent.displayQuery();
        if (label == null || label.isBlank()) {
            label = queryIntent.searchQuery();
        }
        upsertSignal(
                userId,
                UserTasteSignalType.QUERY,
                signal,
                label.trim(),
                "SEARCH_REPEAT",
                SEARCH_WEIGHT,
                null,
                now
        );
    }

    @Transactional
    public UserTasteSignalResult updateSignal(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid UpdateUserTasteSignalCommand command
    ) {
        validateUser(profileCommand, command.userId(), "Taste signal user does not match authenticated user");
        userService.ensureProfile(profileCommand);
        UserTasteSignal signal = userTasteSignalRepository.findById(command.signalId())
                .filter(candidate -> candidate.getUserId().equals(command.userId()))
                .orElseThrow(() -> new UserException("Unknown taste signal " + command.signalId()));
        return UserTasteSignalResult.from(userTasteSignalRepository.save(signal.update(
                command.weight(),
                command.disabled(),
                Instant.now()
        )));
    }

    @Transactional
    public void removeSignal(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            UUID userId,
            UUID signalId
    ) {
        validateUser(profileCommand, userId, "Taste signal user does not match authenticated user");
        userService.ensureProfile(profileCommand);
        userTasteSignalRepository.deleteByUserIdAndId(userId, signalId);
    }

    @Transactional
    public UserSettingsResult acceptSuggestion(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid AcceptUserTasteSuggestionCommand command
    ) {
        validateUser(profileCommand, command.userId(), "Taste suggestion user does not match authenticated user");
        if (!shoppingFilterRepository.existsById(command.filterId())) {
            throw new UserException("Unknown filter " + command.filterId());
        }
        userService.ensureProfile(profileCommand);
        UserSettingsResult settings = userSettingsService.get(profileCommand);
        Set<String> activeFilterIds = settings.filters().stream()
                .map(ShoppingFilterResult::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        activeFilterIds.add(command.filterId());
        UserSettingsResult updated = userSettingsService.update(
                profileCommand,
                new UpdateUserSettingsCommand(
                        command.userId(),
                        null,
                        false,
                        null,
                        null,
                        null,
                        activeFilterIds,
                        Set.of(),
                        List.of()
                )
        );
        Instant now = Instant.now();
        userTasteSignalRepository.findByUserIdAndSuggestedFilterId(command.userId(), command.filterId())
                .forEach(signal -> userTasteSignalRepository.save(signal.acceptSuggestion(now)));
        return updated;
    }

    @Transactional
    public void rejectSuggestion(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid AcceptUserTasteSuggestionCommand command
    ) {
        validateUser(profileCommand, command.userId(), "Taste suggestion user does not match authenticated user");
        userService.ensureProfile(profileCommand);
        Instant now = Instant.now();
        userTasteSignalRepository.findByUserIdAndSuggestedFilterId(command.userId(), command.filterId())
                .forEach(signal -> userTasteSignalRepository.save(signal.rejectSuggestion(now)));
    }

    @Transactional(readOnly = true)
    public UserTasteProfileResult profile(UUID userId, UserSettingsResult settings) {
        List<UserTasteSignal> signals = userTasteSignalRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        List<UserTasteSignalResult> signalResults = signals.stream()
                .map(UserTasteSignalResult::from)
                .toList();
        return new UserTasteProfileResult(
                profileHash(signals),
                signalResults,
                suggestions(signals, settings)
        );
    }

    private void recordProductSignals(
            UUID userId,
            UserTasteBehaviorType behavior,
            SaveUserProductCommand product,
            Instant now
    ) {
        double weight = behaviorWeight(behavior);
        String behaviorName = behavior.name();
        List<String> filterIds = new ArrayList<>();
        filterIds.addAll(safeList(product.satisfies()));
        filterIds.addAll(safeList(product.provides()));
        filterIds.addAll(safeList(product.misses()));
        Map<String, ShoppingFilter> filters = shoppingFilters(filterIds);
        List<TasteSignalMutation> mutations = new ArrayList<>();
        safeList(product.satisfies()).forEach(filterId ->
                addMutation(mutations, filterSignal(filters, filterId, behaviorName, weight)));
        safeList(product.provides()).forEach(filterId ->
                addMutation(mutations, filterSignal(filters, filterId, behaviorName, weight * 0.8d)));
        safeList(product.misses()).forEach(filterId ->
                addMutation(mutations, filterSignal(filters, filterId, behaviorName, -weight)));
        addMutation(mutations, textSignal(UserTasteSignalType.BRAND, product.brand(), behaviorName, weight * 0.7d));
        addMutation(mutations, textSignal(UserTasteSignalType.CATEGORY, product.category(), behaviorName, weight * 0.6d));
        upsertSignals(userId, mutations, now);
    }

    private TasteSignalMutation filterSignal(
            Map<String, ShoppingFilter> filters,
            String filterId,
            String behavior,
            double weight
    ) {
        ShoppingFilter filter = filters.get(filterId);
        if (filter == null) {
            return null;
        }
        return new TasteSignalMutation(
                UserTasteSignalType.FILTER,
                filter.getId(),
                filter.getLabel(),
                behavior,
                weight,
                filter.getId()
        );
    }

    private TasteSignalMutation textSignal(
            UserTasteSignalType signalType,
            String value,
            String behavior,
            double weight
    ) {
        String key = normalized(value);
        if (key == null || key.length() < 2) {
            return null;
        }
        return new TasteSignalMutation(signalType, key, value.trim(), behavior, weight, null);
    }

    private void addMutation(List<TasteSignalMutation> mutations, TasteSignalMutation mutation) {
        if (mutation != null) {
            mutations.add(mutation);
        }
    }

    private void upsertSignal(
            UUID userId,
            UserTasteSignalType signalType,
            String signalKey,
            String label,
            String behavior,
            double weight,
            String suggestedFilterId,
            Instant now
    ) {
        upsertSignals(userId, List.of(new TasteSignalMutation(
                signalType,
                signalKey,
                label,
                behavior,
                weight,
                suggestedFilterId
        )), now);
    }

    private void upsertSignals(UUID userId, List<TasteSignalMutation> mutations, Instant now) {
        if (mutations.isEmpty()) {
            return;
        }
        Set<UserTasteSignalType> signalTypes = mutations.stream()
                .map(TasteSignalMutation::signalType)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> signalKeys = mutations.stream()
                .map(TasteSignalMutation::signalKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<TasteSignalKey, UserTasteSignal> signalsByKey = userTasteSignalRepository
                .findByUserIdAndSignalTypeInAndSignalKeyIn(userId, signalTypes, signalKeys)
                .stream()
                .collect(Collectors.toMap(
                        signal -> new TasteSignalKey(signal.getSignalType(), signal.getSignalKey()),
                        signal -> signal,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<TasteSignalKey, UserTasteSignal> changedSignals = new LinkedHashMap<>();
        for (TasteSignalMutation mutation : mutations) {
            TasteSignalKey key = new TasteSignalKey(mutation.signalType(), mutation.signalKey());
            UserTasteSignal signal = signalsByKey.get(key);
            if (signal == null) {
                signal = UserTasteSignal.create(
                        userId,
                        mutation.signalType(),
                        mutation.signalKey(),
                        mutation.label(),
                        mutation.behavior(),
                        mutation.weight(),
                        mutation.suggestedFilterId(),
                        now
                );
            } else {
                signal.reinforce(
                        mutation.label(),
                        mutation.behavior(),
                        mutation.weight(),
                        mutation.suggestedFilterId(),
                        now
                );
            }
            signalsByKey.put(key, signal);
            changedSignals.put(key, signal);
        }
        if (!changedSignals.isEmpty()) {
            userTasteSignalRepository.saveAll(changedSignals.values());
        }
    }

    private List<UserTasteSuggestionResult> suggestions(List<UserTasteSignal> signals, UserSettingsResult settings) {
        Set<String> explicitFilterIds = settings == null || settings.filters() == null
                ? Set.of()
                : settings.filters().stream()
                        .map(ShoppingFilterResult::id)
                        .collect(Collectors.toSet());
        List<String> suggestedFilterIds = signals.stream()
                .filter(signal -> signal.getSignalType() == UserTasteSignalType.FILTER)
                .map(UserTasteSignal::getSuggestedFilterId)
                .filter(id -> id != null)
                .toList();
        Map<String, ShoppingFilter> filters = shoppingFilters(suggestedFilterIds);
        return signals.stream()
                .filter(signal -> signal.getSignalType() == UserTasteSignalType.FILTER)
                .filter(signal -> signal.getStatus() == UserTasteSignalStatus.ACTIVE)
                .filter(signal -> signal.getSuggestionStatus() == UserTasteSuggestionStatus.PENDING)
                .filter(signal -> signal.getWeight() >= SUGGESTION_THRESHOLD)
                .filter(signal -> signal.getSuggestedFilterId() != null)
                .filter(signal -> !explicitFilterIds.contains(signal.getSuggestedFilterId()))
                .sorted(Comparator.comparingDouble(UserTasteSignal::getWeight).reversed()
                        .thenComparing(UserTasteSignal::getUpdatedAt, Comparator.reverseOrder()))
                .map(signal -> suggestion(signal, filters.get(signal.getSuggestedFilterId())))
                .filter(suggestion -> suggestion != null)
                .toList();
    }

    private UserTasteSuggestionResult suggestion(UserTasteSignal signal, ShoppingFilter filter) {
        if (filter == null) {
            return null;
        }
        return new UserTasteSuggestionResult(
                filter.getId(),
                filter.getLabel(),
                filter.getDescription(),
                "You keep choosing products that match " + filter.getLabel() + ".",
                signal.getWeight()
        );
    }

    private Map<String, ShoppingFilter> shoppingFilters(Collection<String> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return shoppingFilterRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(
                        ShoppingFilter::getId,
                        filter -> filter,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private double behaviorWeight(UserTasteBehaviorType behavior) {
        return switch (behavior) {
            case SAVE -> SAVE_WEIGHT;
            case PURCHASE -> PURCHASE_WEIGHT;
            case DISMISS -> DISMISS_WEIGHT;
        };
    }

    private String profileHash(List<UserTasteSignal> signals) {
        String value = signals.stream()
                .filter(signal -> signal.getStatus() == UserTasteSignalStatus.ACTIVE)
                .filter(signal -> Math.abs(signal.getWeight()) >= 0.25d)
                .sorted(Comparator.comparing(UserTasteSignal::getSignalType)
                        .thenComparing(UserTasteSignal::getSignalKey))
                .limit(RANKING_SIGNAL_LIMIT)
                .map(signal -> signal.getSignalType()
                        + "|"
                        + signal.getSignalKey()
                        + "|"
                        + Math.round(signal.getWeight() * 100.0d) / 100.0d)
                .collect(Collectors.joining("\n"));
        return sha256(value);
    }

    private String normalized(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return SPACE_PATTERN.matcher(Normalizer.normalize(value, Normalizer.Form.NFKC)
                        .trim()
                        .toLowerCase(Locale.ROOT))
                .replaceAll(" ");
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void validateUser(EnsureUserProfileCommand profileCommand, UUID userId, String message) {
        if (!profileCommand.id().equals(userId)) {
            throw UserException.forbidden(message);
        }
    }
}
