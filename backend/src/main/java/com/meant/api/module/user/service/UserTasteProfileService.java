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
import com.meant.api.module.user.service.command.UpsertUserCommand;
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

    @Transactional
    public UserTasteProfileResult get(
            @NotNull @Valid UpsertUserCommand upsertCommand,
            @NotNull @Valid GetUserTasteProfileQuery query
    ) {
        validateUser(upsertCommand, query.userId(), "Taste profile user does not match authenticated user");
        userService.upsert(upsertCommand);
        UserSettingsResult settings = userSettingsService.get(upsertCommand);
        return profile(query.userId(), settings);
    }

    @Transactional
    public UserTasteProfileResult recordBehavior(
            @NotNull @Valid UpsertUserCommand upsertCommand,
            @NotNull @Valid RecordUserTasteBehaviorCommand command
    ) {
        validateUser(upsertCommand, command.userId(), "Taste behavior user does not match authenticated user");
        validateUser(upsertCommand, command.product().userId(), "Taste behavior product user does not match authenticated user");
        userService.upsert(upsertCommand);
        recordProductSignals(command.userId(), command.behavior(), command.product(), Instant.now());
        return profile(command.userId(), userSettingsService.get(upsertCommand));
    }

    @Transactional
    public void recordSavedProduct(UUID userId, SaveUserProductCommand command, Instant now) {
        recordProductSignals(userId, UserTasteBehaviorType.SAVE, command, now);
    }

    @Transactional
    public void recordSearch(UUID userId, UserProductSearchQueryIntentResult queryIntent, Instant now) {
        String signal = normalized(queryIntent.searchQuery());
        if (signal == null || signal.length() < 3) {
            return;
        }
        upsertSignal(
                userId,
                UserTasteSignalType.QUERY,
                signal,
                queryIntent.displayQuery(),
                "SEARCH_REPEAT",
                SEARCH_WEIGHT,
                null,
                now
        );
    }

    @Transactional
    public UserTasteSignalResult updateSignal(
            @NotNull @Valid UpsertUserCommand upsertCommand,
            @NotNull @Valid UpdateUserTasteSignalCommand command
    ) {
        validateUser(upsertCommand, command.userId(), "Taste signal user does not match authenticated user");
        userService.upsert(upsertCommand);
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
            @NotNull @Valid UpsertUserCommand upsertCommand,
            UUID userId,
            UUID signalId
    ) {
        validateUser(upsertCommand, userId, "Taste signal user does not match authenticated user");
        userService.upsert(upsertCommand);
        userTasteSignalRepository.deleteByUserIdAndId(userId, signalId);
    }

    @Transactional
    public UserSettingsResult acceptSuggestion(
            @NotNull @Valid UpsertUserCommand upsertCommand,
            @NotNull @Valid AcceptUserTasteSuggestionCommand command
    ) {
        validateUser(upsertCommand, command.userId(), "Taste suggestion user does not match authenticated user");
        userService.upsert(upsertCommand);
        UserSettingsResult settings = userSettingsService.get(upsertCommand);
        Set<String> activeFilterIds = settings.filters().stream()
                .map(ShoppingFilterResult::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        activeFilterIds.add(command.filterId());
        UserSettingsResult updated = userSettingsService.update(
                upsertCommand,
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
            @NotNull @Valid UpsertUserCommand upsertCommand,
            @NotNull @Valid AcceptUserTasteSuggestionCommand command
    ) {
        validateUser(upsertCommand, command.userId(), "Taste suggestion user does not match authenticated user");
        userService.upsert(upsertCommand);
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
        Map<String, ShoppingFilter> filters = shoppingFilters();
        safeList(product.satisfies()).forEach(filterId -> filterSignal(userId, filters, filterId, behaviorName, weight, now));
        safeList(product.provides()).forEach(filterId -> filterSignal(userId, filters, filterId, behaviorName, weight * 0.8d, now));
        safeList(product.misses()).forEach(filterId -> filterSignal(userId, filters, filterId, behaviorName, -weight, now));
        textSignal(userId, UserTasteSignalType.BRAND, product.brand(), behaviorName, weight * 0.7d, now);
        textSignal(userId, UserTasteSignalType.CATEGORY, product.category(), behaviorName, weight * 0.6d, now);
    }

    private void filterSignal(
            UUID userId,
            Map<String, ShoppingFilter> filters,
            String filterId,
            String behavior,
            double weight,
            Instant now
    ) {
        ShoppingFilter filter = filters.get(filterId);
        if (filter == null) {
            return;
        }
        upsertSignal(
                userId,
                UserTasteSignalType.FILTER,
                filter.getId(),
                filter.getLabel(),
                behavior,
                weight,
                filter.getId(),
                now
        );
    }

    private void textSignal(
            UUID userId,
            UserTasteSignalType signalType,
            String value,
            String behavior,
            double weight,
            Instant now
    ) {
        String key = normalized(value);
        if (key == null || key.length() < 2) {
            return;
        }
        upsertSignal(userId, signalType, key, value.trim(), behavior, weight, null, now);
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
        userTasteSignalRepository.save(userTasteSignalRepository
                .findByUserIdAndSignalTypeAndSignalKey(userId, signalType, signalKey)
                .map(existing -> existing.reinforce(label, behavior, weight, suggestedFilterId, now))
                .orElseGet(() -> UserTasteSignal.create(
                        userId,
                        signalType,
                        signalKey,
                        label,
                        behavior,
                        weight,
                        suggestedFilterId,
                        now
                )));
    }

    private List<UserTasteSuggestionResult> suggestions(List<UserTasteSignal> signals, UserSettingsResult settings) {
        Set<String> explicitFilterIds = settings.filters().stream()
                .map(ShoppingFilterResult::id)
                .collect(Collectors.toSet());
        Map<String, ShoppingFilter> filters = shoppingFilters();
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

    private Map<String, ShoppingFilter> shoppingFilters() {
        return shoppingFilterRepository.findAll().stream()
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

    private void validateUser(UpsertUserCommand upsertCommand, UUID userId, String message) {
        if (!upsertCommand.id().equals(userId)) {
            throw UserException.forbidden(message);
        }
    }
}
