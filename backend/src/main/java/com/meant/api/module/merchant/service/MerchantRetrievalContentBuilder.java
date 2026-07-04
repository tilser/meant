package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.repository.MerchantCategoryRepository;
import com.meant.api.module.merchant.repository.MerchantPopularSearchRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MerchantRetrievalContentBuilder {

    private final MerchantCategoryRepository merchantCategoryRepository;
    private final MerchantPopularSearchRepository merchantPopularSearchRepository;

    public Optional<String> build(Merchant merchant) {
        return buildAll(List.of(merchant)).getOrDefault(merchant.getId(), Optional.empty());
    }

    public Map<UUID, Optional<String>> buildAll(List<Merchant> merchants) {
        if (merchants.isEmpty()) {
            return Map.of();
        }

        List<UUID> merchantIds = merchants.stream()
                .map(Merchant::getId)
                .toList();
        Map<UUID, List<String>> categoriesByMerchantId = categoriesByMerchantId(merchantIds);
        Map<UUID, List<String>> popularSearchesByMerchantId = popularSearchesByMerchantId(merchantIds);
        Map<UUID, Optional<String>> contentByMerchantId = new HashMap<>();

        merchants.forEach(merchant -> contentByMerchantId.put(
                merchant.getId(),
                buildContent(
                        categoriesByMerchantId.getOrDefault(merchant.getId(), List.of()),
                        popularSearchesByMerchantId.getOrDefault(merchant.getId(), List.of())
                )
        ));
        return contentByMerchantId;
    }

    private Map<UUID, List<String>> categoriesByMerchantId(Collection<UUID> merchantIds) {
        Map<UUID, List<String>> valuesByMerchantId = new HashMap<>();
        merchantCategoryRepository.findValuesByMerchantIdIn(merchantIds)
                .forEach(category -> addValue(valuesByMerchantId, category.getMerchantId(), category.getName()));
        return valuesByMerchantId;
    }

    private Map<UUID, List<String>> popularSearchesByMerchantId(Collection<UUID> merchantIds) {
        Map<UUID, List<String>> valuesByMerchantId = new HashMap<>();
        merchantPopularSearchRepository.findValuesByMerchantIdIn(merchantIds)
                .forEach(popularSearch -> addValue(
                        valuesByMerchantId,
                        popularSearch.getMerchantId(),
                        popularSearch.getSearchText()
                ));
        return valuesByMerchantId;
    }

    private void addValue(Map<UUID, List<String>> valuesByMerchantId, UUID merchantId, String value) {
        valuesByMerchantId.computeIfAbsent(merchantId, _ -> new ArrayList<>()).add(value);
    }

    private Optional<String> buildContent(List<String> categoryValues, List<String> popularSearchValues) {
        List<String> categories = normalizedValues(categoryValues);
        List<String> popularSearches = normalizedValues(popularSearchValues);

        if (categories.isEmpty() && popularSearches.isEmpty()) {
            return Optional.empty();
        }

        StringBuilder content = new StringBuilder();
        if (!categories.isEmpty()) {
            content.append("Categories: ").append(String.join(", ", categories));
        }
        if (!popularSearches.isEmpty()) {
            if (!content.isEmpty()) {
                content.append('\n');
            }
            content.append("Popular searches: ").append(String.join(", ", popularSearches));
        }
        return Optional.of(content.toString());
    }

    private List<String> normalizedValues(List<String> values) {
        TreeMap<String, String> normalizedValues = new TreeMap<>();
        values.stream()
                .map(this::trimToNull)
                .flatMap(Optional::stream)
                .forEach(value -> normalizedValues.putIfAbsent(normalizeKey(value), value));
        return List.copyOf(normalizedValues.values());
    }

    private Optional<String> trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value.trim());
    }

    private String normalizeKey(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
