package com.meant.api.module.user.service;

import com.meant.api.module.user.constant.UserProductSearchPagination;
import com.meant.api.module.user.constant.UserTasteSignalStatus;
import com.meant.api.module.user.service.dto.ShoppingFilterResult;
import com.meant.api.module.user.service.dto.UserInventoryRecommendationSignal;
import com.meant.api.module.user.service.dto.UserProductSearchPreparation;
import com.meant.api.module.user.service.dto.UserTasteSignalResult;
import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.ProductRankingContext;
import com.meant.api.module.catalog.service.dto.ProductRankingContext.PreferenceSignal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Maps existing settings, taste, and one-shot inventory facts into the neutral ranking contract. */
@Component
public class UserProductRankingContextFactory {

    private static final Pattern SPACE = Pattern.compile("\\s+");
    private static final Pattern AVOID_PREFIX = Pattern.compile("^(?:no|avoid|without)\\s+");
    private static final int MAX_PREFERENCE_SIGNALS = 40;

    private final UserInventoryService userInventoryService;
    private final UserProductSearchProfileSuppressionPolicy profileSuppressionPolicy;

    public UserProductRankingContextFactory(UserInventoryService userInventoryService) {
        this(userInventoryService, new UserProductSearchProfileSuppressionPolicy());
    }

    @Autowired
    public UserProductRankingContextFactory(
            UserInventoryService userInventoryService,
            UserProductSearchProfileSuppressionPolicy profileSuppressionPolicy
    ) {
        this.userInventoryService = userInventoryService;
        this.profileSuppressionPolicy = profileSuppressionPolicy;
    }

    public ProductRankingContext create(
            UUID userId,
            UserProductSearchPreparation preparation,
            List<CanonicalProduct> products
    ) {
        Map<String, ProductRankingContext.InventoryRelationship> inventory = userInventoryService
                .canonicalRecommendationSignals(userId, products)
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> relationship(entry.getValue()),
                        (left, right) -> left
                ));
        return context(preparation, preferences(preparation), inventory);
    }

    private static ProductRankingContext context(
            UserProductSearchPreparation preparation,
            List<PreferenceSignal> preferences,
            Map<String, ProductRankingContext.InventoryRelationship> inventory
    ) {
        String intent = preparation.queryIntent() == null
                ? preparation.catalogInput().searchQuery()
                : preparation.queryIntent().normalizedSearchQuery();
        return new ProductRankingContext(
                normalized(intent),
                preparation.catalogInput().context(),
                preparation.catalogInput().filters(),
                preferences,
                inventory,
                preparation.now(),
                UserProductSearchPagination.MAX_LIMIT
        );
    }

    private List<PreferenceSignal> preferences(UserProductSearchPreparation preparation) {
        List<PreferenceSignal> signals = new ArrayList<>();
        Set<String> explicitFilterIds = new HashSet<>();
        if (preparation.settings() != null && preparation.settings().filters() != null) {
            for (ShoppingFilterResult filter : preparation.settings().filters()) {
                if (filter == null || filter.id() == null || filter.id().isBlank()) {
                    continue;
                }
                if (profileSuppressionPolicy.suppressed(
                        filter,
                        preparation.profileSuppressionTargets()
                )) {
                    continue;
                }
                explicitFilterIds.add(filter.id());
                String value = normalized(firstText(filter.label(), filter.description(), filter.id()));
                if (value != null) {
                    int weight = avoided(filter) ? -8_000 : 8_000;
                    String target = weight < 0 ? AVOID_PREFIX.matcher(value).replaceFirst("") : value;
                    if (target.isBlank()) {
                        continue;
                    }
                    signals.add(new PreferenceSignal(
                            PreferenceSignal.Type.FILTER,
                            filter.id(),
                            target,
                            weight
                    ));
                }
            }
        }
        if (preparation.tasteProfile() != null && preparation.tasteProfile().signals() != null) {
            preparation.tasteProfile().signals().stream()
                    .filter(Objects::nonNull)
                    .filter(signal -> signal.status() == UserTasteSignalStatus.ACTIVE)
                    .filter(signal -> !profileSuppressionPolicy.suppressed(
                            signal,
                            preparation.profileSuppressionTargets()
                    ))
                    .filter(signal -> !explicitFilterIds.contains(signal.signalKey()))
                    .map(this::preference)
                    .filter(Objects::nonNull)
                    .forEach(signals::add);
        }
        return signals.stream().limit(MAX_PREFERENCE_SIGNALS).toList();
    }

    private PreferenceSignal preference(UserTasteSignalResult signal) {
        PreferenceSignal.Type type = PreferenceSignal.Type.valueOf(signal.signalType().name());
        String value = normalized(type == PreferenceSignal.Type.FILTER
                ? signal.label()
                : firstText(signal.label(), signal.signalKey()));
        if (signal.signalKey() == null || signal.signalKey().isBlank()
                || value == null || signal.weight() == 0.0d) {
            return null;
        }
        int weight = Math.max(-10_000, Math.min(10_000, (int) Math.round(signal.weight() * 2_500.0d)));
        String target = type == PreferenceSignal.Type.FILTER && weight < 0
                ? AVOID_PREFIX.matcher(value).replaceFirst("")
                : value;
        if (target.isBlank()) {
            return null;
        }
        return weight == 0 ? null : new PreferenceSignal(
                type,
                signal.signalKey(),
                target,
                weight
        );
    }

    private boolean avoided(ShoppingFilterResult filter) {
        String polarity = normalized(filter.polarity());
        String label = normalized(filter.label());
        return "avoid".equals(polarity)
                || "negative".equals(polarity)
                || label != null && AVOID_PREFIX.matcher(label).find();
    }

    private ProductRankingContext.InventoryRelationship relationship(UserInventoryRecommendationSignal signal) {
        return ProductRankingContext.InventoryRelationship.valueOf(signal.relationship().name());
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String normalized(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return SPACE.matcher(Normalizer.normalize(value, Normalizer.Form.NFKC)
                        .trim()
                        .toLowerCase(Locale.ROOT))
                .replaceAll(" ");
    }
}
