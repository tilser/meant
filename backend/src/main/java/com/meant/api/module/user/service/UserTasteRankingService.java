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

    public List<UserProductSearchProductResult> rank(
            List<UserProductSearchProductResult> products,
            UserTasteProfileResult tasteProfile,
            UserSettingsResult settings
    ) {
        if (tasteProfile == null || tasteProfile.signals().isEmpty() || products.isEmpty()) {
            return products;
        }
        Set<String> explicitFilterIds = settings == null ? Set.of() : settings.filters().stream()
                .map(filter -> filter.id())
                .collect(Collectors.toSet());
        return products.stream()
                .map(product -> product.withMatchScore(adjustedScore(product, tasteProfile.signals(), explicitFilterIds)))
                .sorted(Comparator.comparingInt(UserProductSearchProductResult::matchScore)
                        .reversed()
                        .thenComparingInt(UserProductSearchProductResult::rank))
                .toList();
    }

    private int adjustedScore(
            UserProductSearchProductResult product,
            List<UserTasteSignalResult> signals,
            Set<String> explicitFilterIds
    ) {
        double adjustment = signals.stream()
                .filter(signal -> signal.status() == UserTasteSignalStatus.ACTIVE)
                .filter(signal -> !explicitFilterIds.contains(signal.signalKey()))
                .mapToDouble(signal -> contribution(product, signal))
                .sum();
        return Math.max(25, Math.min(99, product.matchScore() + (int) Math.round(adjustment)));
    }

    private double contribution(UserProductSearchProductResult product, UserTasteSignalResult signal) {
        return switch (signal.signalType()) {
            case FILTER -> filterContribution(product, signal);
            case BRAND -> textContains(brandText(product), signal.signalKey()) ? signal.weight() * 1.5d : 0.0d;
            case CATEGORY -> textContains(categoryText(product), signal.signalKey()) ? signal.weight() * 1.2d : 0.0d;
            case MATERIAL -> listContains(product.materials(), signal.signalKey()) ? signal.weight() * 1.4d : 0.0d;
            case CERTIFICATION -> listContains(product.certifications(), signal.signalKey()) ? signal.weight() * 1.4d : 0.0d;
            case QUERY -> textContains(searchableText(product), signal.signalKey()) ? signal.weight() * 0.8d : 0.0d;
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

    private boolean listContains(List<String> values, String signal) {
        return values.stream().anyMatch(value -> textContains(value, signal));
    }

    private String searchableText(UserProductSearchProductResult product) {
        return Stream.of(
                        product.title(),
                        product.merchantName(),
                        product.detailDescription(),
                        product.descriptionHtml(),
                        categoryText(product),
                        brandText(product)
                )
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(" "));
    }

    private String brandText(UserProductSearchProductResult product) {
        return Stream.of(product.merchantName(), product.merchantDomain())
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(" "));
    }

    private String categoryText(UserProductSearchProductResult product) {
        return product.categories().stream()
                .map(category -> category.value())
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(" "));
    }

    private boolean textContains(String value, String signal) {
        String normalizedValue = normalized(value);
        return normalizedValue != null && normalizedValue.contains(signal);
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
}
