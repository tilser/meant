package com.meant.api.module.discount.service;

import com.meant.api.module.discount.service.dto.DiscountCodeCandidateSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class DiscountCodeCandidateNormalizationService {

    public List<DiscountCodeCandidateSource> normalize(
            List<DiscountCodeCandidateSource> candidates,
            int maxCandidates
    ) {
        if (candidates == null || candidates.isEmpty() || maxCandidates <= 0) {
            return List.of();
        }

        LinkedHashMap<String, DiscountCodeCandidateSource> deduped = new LinkedHashMap<>();
        for (DiscountCodeCandidateSource candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            String code = normalizeCode(candidate.code());
            if (code == null) {
                continue;
            }
            String key = key(code);
            DiscountCodeCandidateSource normalized = candidate.withCode(code);
            DiscountCodeCandidateSource existing = deduped.get(key);
            if (existing == null || shouldPrefer(normalized.code(), existing.code())) {
                if (existing == null && deduped.size() >= maxCandidates) {
                    continue;
                }
                deduped.put(key, normalized);
            }
        }
        return List.copyOf(deduped.values());
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private boolean shouldPrefer(String candidateCode, String existingCode) {
        return isUppercaseCode(candidateCode) && !isUppercaseCode(existingCode);
    }

    private boolean isUppercaseCode(String code) {
        return code != null
                && !code.equals(code.toLowerCase(Locale.ROOT))
                && code.equals(code.toUpperCase(Locale.ROOT));
    }

    private String key(String code) {
        return code.toLowerCase(Locale.ROOT);
    }
}
