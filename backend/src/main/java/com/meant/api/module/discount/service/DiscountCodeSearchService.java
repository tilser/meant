package com.meant.api.module.discount.service;

import com.meant.api.module.discount.constant.DiscountCodeSearchStatus;
import com.meant.api.module.discount.constant.DiscountCodeStatus;
import com.meant.api.module.discount.exception.DiscountCodeException;
import com.meant.api.module.discount.properties.DiscountCodeSearchProperties;
import com.meant.api.module.discount.service.command.SearchDiscountCodesCommand;
import com.meant.api.module.discount.service.dto.CachedDiscountCodeCandidate;
import com.meant.api.module.discount.service.dto.DiscountCodeCacheResult;
import com.meant.api.module.discount.service.dto.DiscountCodeCandidateEvaluation;
import com.meant.api.module.discount.service.dto.DiscountCodeCandidateSource;
import com.meant.api.module.discount.service.dto.DiscountCodeResult;
import com.meant.api.module.discount.service.dto.DiscountCodeSearchResult;
import com.meant.api.module.discount.service.dto.DiscountMerchant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class DiscountCodeSearchService {

    private final DiscountMerchantLookupService merchantLookupService;
    private final DiscountCodePersistenceService persistenceService;
    private final DiscountCodeWebSearchService webSearchService;
    private final DiscountCodeValidationService validationService;
    private final DiscountCodeSearchProperties properties;

    public DiscountCodeSearchResult search(@NotNull @Valid SearchDiscountCodesCommand command) {
        Instant now = Instant.now();
        DiscountMerchant merchant = merchantLookupService.find(command.merchantId(), command.merchantDomain());
        DiscountCodeCacheResult cacheHit = persistenceService.findFreshValidCodes(merchant.id(), now).orElse(null);
        if (cacheHit != null && !cacheHit.codes().isEmpty()) {
            return new DiscountCodeSearchResult(
                    merchant.id(),
                    merchant.domain(),
                    true,
                    cacheHit.searchedAt(),
                    cacheHit.expiresAt(),
                    cacheHit.codes()
            );
        }

        List<DiscountCodeCandidateSource> candidates = discoverCandidates(merchant, command, now);
        if (candidates.isEmpty()) {
            Instant expiresAt = now.plus(properties.failedCacheTtl());
            persistenceService.saveSearch(
                    merchant,
                    DiscountCodeSearchStatus.NO_CODES_FOUND,
                    0,
                    null,
                    now,
                    expiresAt,
                    List.of()
            );
            return new DiscountCodeSearchResult(merchant.id(), merchant.domain(), false, now, expiresAt, List.of());
        }

        List<DiscountCodeCandidateEvaluation> evaluations = evaluateCandidates(merchant, command, candidates, now);
        Instant expiresAt = searchExpiresAt(evaluations, now);
        persistenceService.saveSearch(
                merchant,
                DiscountCodeSearchStatus.COMPLETED,
                candidates.size(),
                null,
                now,
                expiresAt,
                evaluations
        );
        return new DiscountCodeSearchResult(
                merchant.id(),
                merchant.domain(),
                false,
                now,
                responseExpiresAt(evaluations, expiresAt),
                validResults(evaluations)
        );
    }

    private List<DiscountCodeCandidateSource> discoverCandidates(
            DiscountMerchant merchant,
            SearchDiscountCodesCommand command,
            Instant now
    ) {
        try {
            return webSearchService.search(merchant, command, now);
        } catch (DiscountCodeException exception) {
            persistenceService.saveSearch(
                    merchant,
                    DiscountCodeSearchStatus.FAILED_RETRYABLE,
                    0,
                    exception.getMessage(),
                    now,
                    now.plus(properties.failedCacheTtl()),
                    List.of()
            );
            throw exception;
        }
    }

    private List<DiscountCodeCandidateEvaluation> evaluateCandidates(
            DiscountMerchant merchant,
            SearchDiscountCodesCommand command,
            List<DiscountCodeCandidateSource> candidates,
            Instant now
    ) {
        Map<String, CachedDiscountCodeCandidate> cachedNonValid = persistenceService.findFreshNonValidCandidates(
                merchant.id(),
                normalizedCodes(candidates),
                now
        );
        List<DiscountCodeCandidateEvaluation> evaluations = new ArrayList<>();
        for (DiscountCodeCandidateSource candidate : candidates) {
            CachedDiscountCodeCandidate cachedCandidate = cachedNonValid.get(key(candidate.code()));
            if (cachedCandidate != null) {
                evaluations.add(DiscountCodeCandidateEvaluation.fromCached(candidate, cachedCandidate));
                continue;
            }
            evaluations.add(validationService.validate(merchant, command, candidate, now));
        }
        return List.copyOf(evaluations);
    }

    private List<String> normalizedCodes(List<DiscountCodeCandidateSource> candidates) {
        LinkedHashMap<String, String> codes = new LinkedHashMap<>();
        candidates.forEach(candidate -> codes.putIfAbsent(key(candidate.code()), key(candidate.code())));
        return List.copyOf(codes.values());
    }

    private List<DiscountCodeResult> validResults(List<DiscountCodeCandidateEvaluation> evaluations) {
        return evaluations.stream()
                .filter(evaluation -> evaluation.status() == DiscountCodeStatus.VALID)
                .map(DiscountCodeResult::from)
                .toList();
    }

    private Instant searchExpiresAt(List<DiscountCodeCandidateEvaluation> evaluations, Instant now) {
        return evaluations.stream()
                .filter(evaluation -> evaluation.status() == DiscountCodeStatus.VALID)
                .map(DiscountCodeCandidateEvaluation::expiresAt)
                .min(Instant::compareTo)
                .orElse(now.plus(properties.failedCacheTtl()));
    }

    private Instant responseExpiresAt(
            List<DiscountCodeCandidateEvaluation> evaluations,
            Instant fallback
    ) {
        return validResults(evaluations).stream()
                .map(DiscountCodeResult::expiresAt)
                .min(Instant::compareTo)
                .orElse(fallback);
    }

    private String key(String code) {
        return code.toLowerCase(Locale.ROOT);
    }
}
