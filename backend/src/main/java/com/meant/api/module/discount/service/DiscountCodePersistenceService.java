package com.meant.api.module.discount.service;

import com.meant.api.module.discount.constant.DiscountCodeSearchStatus;
import com.meant.api.module.discount.constant.DiscountCodeStatus;
import com.meant.api.module.discount.entity.DiscountCodeCandidate;
import com.meant.api.module.discount.entity.DiscountCodeSearch;
import com.meant.api.module.discount.repository.DiscountCodeCandidateRepository;
import com.meant.api.module.discount.repository.DiscountCodeSearchRepository;
import com.meant.api.module.discount.service.dto.CachedDiscountCodeCandidate;
import com.meant.api.module.discount.service.dto.DiscountCodeCacheResult;
import com.meant.api.module.discount.service.dto.DiscountCodeCandidateEvaluation;
import com.meant.api.module.discount.service.dto.DiscountCodeCandidateSource;
import com.meant.api.module.discount.service.dto.DiscountCodeResult;
import com.meant.api.module.discount.service.dto.DiscountMerchant;
import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.repository.MerchantRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DiscountCodePersistenceService {

    private final DiscountCodeSearchRepository searchRepository;
    private final DiscountCodeCandidateRepository candidateRepository;
    private final MerchantRepository merchantRepository;

    @Transactional(readOnly = true)
    public Optional<DiscountCodeCacheResult> findFreshValidCodes(UUID merchantId, Instant now) {
        List<DiscountCodeCandidate> candidates = candidateRepository.findFreshValid(
                merchantId,
                DiscountCodeStatus.VALID,
                now
        );
        LinkedHashMap<String, DiscountCodeResult> deduped = new LinkedHashMap<>();
        Instant searchedAt = null;
        for (DiscountCodeCandidate candidate : candidates) {
            deduped.putIfAbsent(key(candidate.getCode()), toResult(candidate));
            Instant candidateSearchedAt = candidate.getSearch().getSearchedAt();
            if (searchedAt == null || candidateSearchedAt.isAfter(searchedAt)) {
                searchedAt = candidateSearchedAt;
            }
        }
        if (deduped.isEmpty()) {
            return Optional.empty();
        }
        List<DiscountCodeResult> codes = List.copyOf(deduped.values());
        return Optional.of(new DiscountCodeCacheResult(searchedAt, responseExpiresAt(codes), codes));
    }

    @Transactional(readOnly = true)
    public Optional<DiscountCodeCacheResult> findFreshSearch(UUID merchantId, Instant now) {
        return searchRepository.findFirstByMerchant_IdAndExpiresAtAfterOrderBySearchedAtDesc(merchantId, now)
                .filter(search -> search.getStatus() == DiscountCodeSearchStatus.COMPLETED
                        || search.getStatus() == DiscountCodeSearchStatus.NO_CODES_FOUND)
                .map(search -> new DiscountCodeCacheResult(search.getSearchedAt(), search.getExpiresAt(), List.of()));
    }

    @Transactional(readOnly = true)
    public Map<String, CachedDiscountCodeCandidate> findFreshNonValidCandidates(
            UUID merchantId,
            Collection<String> normalizedCodes,
            Instant now
    ) {
        if (normalizedCodes == null || normalizedCodes.isEmpty()) {
            return Map.of();
        }
        List<DiscountCodeCandidate> candidates = candidateRepository.findFreshByNormalizedCodes(
                merchantId,
                List.of(DiscountCodeStatus.INVALID, DiscountCodeStatus.FAILED_RETRYABLE),
                normalizedCodes,
                now
        );
        LinkedHashMap<String, CachedDiscountCodeCandidate> results = new LinkedHashMap<>();
        candidates.forEach(candidate -> results.putIfAbsent(key(candidate.getCode()), new CachedDiscountCodeCandidate(
                candidate.getCode(),
                candidate.getStatus(),
                candidate.getValidUntil(),
                candidate.getValidatedAt(),
                candidate.getExpiresAt(),
                candidate.getValidationMessage()
        )));
        return results;
    }

    @Transactional
    public void saveSearch(
            DiscountMerchant merchant,
            DiscountCodeSearchStatus status,
            int sourceCount,
            String errorMessage,
            Instant searchedAt,
            Instant expiresAt,
            List<DiscountCodeCandidateEvaluation> evaluations
    ) {
        Merchant merchantReference = merchantRepository.getReferenceById(merchant.id());
        Instant now = Instant.now();
        DiscountCodeSearch search = searchRepository.save(DiscountCodeSearch.builder()
                .merchant(merchantReference)
                .merchantDomain(merchant.domain())
                .status(status)
                .searchedAt(searchedAt)
                .expiresAt(expiresAt)
                .sourceCount(sourceCount)
                .errorMessage(errorMessage)
                .createdAt(now)
                .updatedAt(now)
                .build());

        for (int index = 0; index < evaluations.size(); index++) {
            DiscountCodeCandidateEvaluation evaluation = evaluations.get(index);
            candidateRepository.save(toEntity(search, merchantReference, evaluation, index, now));
        }
    }

    private DiscountCodeCandidate toEntity(
            DiscountCodeSearch search,
            Merchant merchant,
            DiscountCodeCandidateEvaluation evaluation,
            int displayOrder,
            Instant now
    ) {
        DiscountCodeCandidateSource candidate = evaluation.candidate();
        return DiscountCodeCandidate.builder()
                .search(search)
                .merchant(merchant)
                .code(candidate.code())
                .status(evaluation.status())
                .title(candidate.title())
                .description(candidate.description())
                .sourceUrl(candidate.sourceUrl())
                .confidence(candidate.confidence())
                .restrictions(candidate.restrictions())
                .validFromText(candidate.validFromText())
                .validUntilText(candidate.validUntilText())
                .validUntil(evaluation.validUntil())
                .validatedAt(evaluation.validatedAt())
                .expiresAt(evaluation.expiresAt())
                .validationMessage(evaluation.validationMessage())
                .displayOrder(displayOrder)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private DiscountCodeResult toResult(DiscountCodeCandidate candidate) {
        return new DiscountCodeResult(
                candidate.getCode(),
                candidate.getTitle(),
                candidate.getDescription(),
                candidate.getSourceUrl(),
                candidate.getConfidence(),
                candidate.getRestrictions(),
                candidate.getValidUntil(),
                candidate.getExpiresAt(),
                candidate.getValidationMessage()
        );
    }

    private Instant responseExpiresAt(List<DiscountCodeResult> codes) {
        return codes.stream()
                .map(DiscountCodeResult::expiresAt)
                .min(Instant::compareTo)
                .orElse(null);
    }

    private String key(String code) {
        return code.toLowerCase(Locale.ROOT);
    }
}
