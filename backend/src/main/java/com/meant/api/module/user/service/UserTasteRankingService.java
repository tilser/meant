package com.meant.api.module.user.service;

import com.meant.api.module.user.constant.UserTasteSignalStatus;
import com.meant.api.module.user.constant.UserTasteSignalType;
import com.meant.api.module.user.service.dto.UserProductSearchProductResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.dto.UserTasteProfileResult;
import com.meant.api.module.user.service.dto.UserTasteSignalResult;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class UserTasteRankingService {

    private static final Pattern SPACE_PATTERN = Pattern.compile("\\s+");
    private static final double SIGNAL_WEIGHT_THRESHOLD = 0.25d;
    private static final int RANKING_SIGNAL_LIMIT = 30;

    public List<UserProductSearchProductResult> rank(
            List<UserProductSearchProductResult> products,
            UserTasteProfileResult tasteProfile,
            UserSettingsResult settings
    ) {
        if (tasteProfile == null || tasteProfile.signals().isEmpty() || products.isEmpty()) {
            return products;
        }
        Set<String> explicitFilterIds = settings == null || settings.filters() == null ? Set.of()
                : settings.filters().stream()
                        .map(filter -> filter.id())
                        .collect(Collectors.toSet());
        // Resolve the applicable signals once instead of re-filtering them for every product.
        List<UserTasteSignalResult> activeSignals = tasteProfile.signals().stream()
                .filter(signal -> signal.status() == UserTasteSignalStatus.ACTIVE)
                .filter(signal -> Math.abs(signal.weight()) >= SIGNAL_WEIGHT_THRESHOLD)
                .filter(signal -> !explicitFilterIds.contains(signal.signalKey()))
                .limit(RANKING_SIGNAL_LIMIT)
                .toList();
        if (activeSignals.isEmpty()) {
            return products;
        }
        return products.stream()
                .map(product -> product.withMatchScore(adjustedScore(product, activeSignals)))
                .sorted(Comparator.comparingInt(UserProductSearchProductResult::matchScore)
                        .reversed()
                        .thenComparingInt(UserProductSearchProductResult::rank))
                .toList();
    }

    private int adjustedScore(
            UserProductSearchProductResult product,
            List<UserTasteSignalResult> signals
    ) {
        ProductText productText = ProductText.from(product);
        double adjustment = signals.stream()
                .mapToDouble(signal -> contribution(product, productText, signal))
                .sum();
        return Math.max(25, Math.min(99, product.matchScore() + (int) Math.round(adjustment)));
    }

    private double contribution(
            UserProductSearchProductResult product,
            ProductText productText,
            UserTasteSignalResult signal
    ) {
        return switch (signal.signalType()) {
            case FILTER -> filterContribution(product, signal);
            case BRAND -> productText.brand().contains(signal.signalKey()) ? signal.weight() * 1.5d : 0.0d;
            case CATEGORY -> productText.category().contains(signal.signalKey()) ? signal.weight() * 1.2d : 0.0d;
            case MATERIAL -> listContains(productText.materials(), signal.signalKey()) ? signal.weight() * 1.4d : 0.0d;
            case CERTIFICATION -> listContains(productText.certifications(), signal.signalKey()) ? signal.weight() * 1.4d : 0.0d;
            case QUERY -> productText.searchable().contains(signal.signalKey()) ? signal.weight() * 0.8d : 0.0d;
        };
    }

    private double filterContribution(UserProductSearchProductResult product, UserTasteSignalResult signal) {
        if (product.matchedFilterIds().contains(signal.signalKey())) {
            return signal.weight() * 2.0d;
        }
        if (product.missedFilterIds().contains(signal.signalKey())) {
            return -signal.weight() * 2.5d;
        }
        return 0.0d;
    }

    private boolean listContains(List<String> normalizedValues, String signal) {
        return normalizedValues.stream().anyMatch(value -> value.contains(signal));
    }

    /**
     * Holds the normalized product strings used during signal evaluation. Normalization
     * (NFKC + lowercase + whitespace collapse) is expensive, so it is computed once per
     * product rather than for every signal.
     */
    private record ProductText(
            String brand,
            String category,
            String searchable,
            List<String> materials,
            List<String> certifications
    ) {

        private static ProductText from(UserProductSearchProductResult product) {
            String brand = normalizedJoin(Stream.of(product.merchantName(), product.merchantDomain()));
            String category = normalizedJoin(safeList(product.categories()).stream()
                    .map(entry -> entry.value()));
            String searchable = normalizedJoin(Stream.of(
                    product.title(),
                    product.merchantName(),
                    product.detailDescription(),
                    product.descriptionHtml(),
                    rawCategory(product),
                    rawBrand(product)
            ));
            return new ProductText(
                    brand,
                    category,
                    searchable,
                    normalizedList(product.materials()),
                    normalizedList(product.certifications())
            );
        }

        private static String rawBrand(UserProductSearchProductResult product) {
            return Stream.of(product.merchantName(), product.merchantDomain())
                    .filter(value -> value != null && !value.isBlank())
                    .collect(Collectors.joining(" "));
        }

        private static String rawCategory(UserProductSearchProductResult product) {
            return safeList(product.categories()).stream()
                    .map(category -> category.value())
                    .filter(value -> value != null && !value.isBlank())
                    .collect(Collectors.joining(" "));
        }

        private static List<String> normalizedList(List<String> values) {
            return safeList(values).stream()
                    .map(UserTasteRankingService::normalized)
                    .filter(value -> value != null)
                    .toList();
        }

        private static <T> List<T> safeList(List<T> values) {
            return values == null ? List.of() : values;
        }

        private static String normalizedJoin(Stream<String> values) {
            String joined = values
                    .filter(value -> value != null && !value.isBlank())
                    .collect(Collectors.joining(" "));
            String normalized = normalized(joined);
            return normalized == null ? "" : normalized;
        }
    }

    private static String normalized(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return SPACE_PATTERN.matcher(Normalizer.normalize(value, Normalizer.Form.NFKC)
                        .trim()
                        .toLowerCase(Locale.ROOT))
                .replaceAll(" ");
    }
}
