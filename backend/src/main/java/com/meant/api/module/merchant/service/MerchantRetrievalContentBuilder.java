package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.entity.MerchantCategory;
import com.meant.api.module.merchant.entity.MerchantPopularSearch;
import com.meant.api.module.merchant.repository.MerchantCategoryRepository;
import com.meant.api.module.merchant.repository.MerchantPopularSearchRepository;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MerchantRetrievalContentBuilder {

    private final MerchantCategoryRepository merchantCategoryRepository;
    private final MerchantPopularSearchRepository merchantPopularSearchRepository;

    public Optional<String> build(Merchant merchant) {
        List<String> categories = normalizedValues(merchantCategoryRepository.findByMerchant(merchant).stream()
                .map(MerchantCategory::getName)
                .toList());
        List<String> popularSearches = normalizedValues(merchantPopularSearchRepository.findByMerchant(merchant).stream()
                .map(MerchantPopularSearch::getSearchText)
                .toList());

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
