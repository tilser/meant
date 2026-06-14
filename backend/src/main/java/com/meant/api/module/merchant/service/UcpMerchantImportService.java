package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.entity.MerchantRaw;
import com.meant.api.module.merchant.properties.MerchantImportProperties;
import com.meant.api.module.merchant.repository.MerchantRepository;
import com.meant.api.module.merchant.repository.MerchantRawRepository;
import com.meant.api.module.merchant.service.dto.HuggingFaceDatasetRow;
import com.meant.api.module.merchant.service.dto.UcpMerchantDatasetRow;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;

@Slf4j
@Service
@Validated
@RequiredArgsConstructor
public class UcpMerchantImportService {

    private static final String VERIFIED_STATUS = "verified";

    private final UcpDatasetClient ucpDatasetClient;
    private final MerchantImportProperties merchantImportProperties;
    private final MerchantRawRepository merchantRawRepository;
    private final MerchantRepository merchantRepository;
    private final TransactionTemplate transactionTemplate;

    public void importMerchants() {
        List<HuggingFaceDatasetRow> datasetRows = ucpDatasetClient.fetchAllRows();
        Instant fetchedAt = Instant.now();
        Set<String> excludedDomains = normalizedExcludedDomains();
        List<HuggingFaceDatasetRow> verifiedRows = datasetRows.stream()
                .filter(datasetRow -> isVerified(datasetRow.row()))
                .filter(datasetRow -> !isExcludedDomain(datasetRow.row(), excludedDomains))
                .toList();
        List<HuggingFaceDatasetRow> deduplicatedRows = deduplicateByDomain(verifiedRows);
        List<MerchantRaw> verifiedMerchants = deduplicatedRows.stream()
                .map(datasetRow -> toMerchantRaw(datasetRow, fetchedAt))
                .toList();

        transactionTemplate.executeWithoutResult(_ -> {
            Set<String> seenDomains = verifiedMerchants.stream()
                    .map(MerchantRaw::getDomain)
                    .collect(Collectors.toSet());
            Map<String, MerchantRaw> existingByDomain = merchantRawRepository.findByDomainIn(seenDomains).stream()
                    .collect(Collectors.toMap(MerchantRaw::getDomain, Function.identity()));

            List<MerchantRaw> merchantsToSave = verifiedMerchants.stream()
                    .map(importedMerchant -> mergeImportedMerchant(
                            importedMerchant,
                            existingByDomain.get(importedMerchant.getDomain())
                    ))
                    .toList();

            merchantRawRepository.saveAll(merchantsToSave);
            List<String> inactiveDomains = merchantRawRepository.findAll().stream()
                    .filter(merchantRaw -> !seenDomains.contains(merchantRaw.getDomain()))
                    .peek(MerchantRaw::markInactive)
                    .map(MerchantRaw::getDomain)
                    .toList();
            if (!inactiveDomains.isEmpty()) {
                merchantRepository.findByDomainIn(inactiveDomains)
                        .forEach(merchant -> merchant.markInactive(fetchedAt));
            }
        });

        log.info("Merchant import completed. Saved merchants: {}", verifiedMerchants.size());
    }

    private boolean isExcludedDomain(UcpMerchantDatasetRow row, Set<String> excludedDomains) {
        if (row == null) {
            return false;
        }
        boolean excluded = excludedDomains.contains(normalizeDomain(row.domain()));
        if (excluded) {
            log.info("Merchant import skipped excluded domain {}", row.domain());
        }
        return excluded;
    }

    private Set<String> normalizedExcludedDomains() {
        return merchantImportProperties.excludedDomains().stream()
                .map(this::normalizeDomain)
                .collect(Collectors.toSet());
    }

    private List<HuggingFaceDatasetRow> deduplicateByDomain(List<HuggingFaceDatasetRow> verifiedRows) {
        Map<String, HuggingFaceDatasetRow> rowsByDomain = verifiedRows.stream()
                .collect(Collectors.toMap(
                        datasetRow -> datasetRow.row().domain(),
                        Function.identity(),
                        (first, _) -> first,
                        LinkedHashMap::new
                ));
        int duplicateCount = verifiedRows.size() - rowsByDomain.size();
        if (duplicateCount > 0) {
            log.warn("Merchant import ignored {} duplicate verified domain rows", duplicateCount);
        }
        return List.copyOf(rowsByDomain.values());
    }

    private boolean isVerified(UcpMerchantDatasetRow row) {
        return row != null && VERIFIED_STATUS.equalsIgnoreCase(row.status().trim());
    }

    private String normalizeDomain(String domain) {
        if (domain == null) {
            return "";
        }
        String normalizedDomain = domain.trim().toLowerCase();
        if (normalizedDomain.startsWith("www.")) {
            return normalizedDomain.substring("www.".length());
        }
        return normalizedDomain;
    }

    private MerchantRaw toMerchantRaw(HuggingFaceDatasetRow datasetRow, Instant fetchedAt) {
        UcpMerchantDatasetRow row = datasetRow.row();

        return MerchantRaw.builder()
                .datasetRowIdx(datasetRow.rowIdx())
                .domain(row.domain())
                .status(row.status())
                .ucpUrl(row.ucpUrl())
                .httpStatus(toHttpStatus(row.httpStatus()))
                .ucpVersion(row.version())
                .hasCheckout(toBoolean(row.hasCheckout()))
                .hasIdentityLinking(toBoolean(row.hasIdentityLinking()))
                .hasCartManagement(toBoolean(row.hasCartManagement()))
                .hasOrder(toBoolean(row.hasOrder()))
                .hasPaymentToken(toBoolean(row.hasPaymentToken()))
                .capabilityCount(row.capabilityCount())
                .aiBotPolicies(row.aiBotPolicies())
                .transports(row.transports())
                .lastCheckedAt(row.lastCheckedAt())
                .lastSuccessAt(row.lastSuccessAt())
                .fetchedAt(fetchedAt)
                .processed(false)
                .processingStatus(null)
                .processingError(null)
                .sourceHash(sourceHash(datasetRow))
                .active(true)
                .lastSeenAt(fetchedAt)
                .build();
    }

    private MerchantRaw mergeImportedMerchant(MerchantRaw importedMerchant, MerchantRaw existingMerchant) {
        if (existingMerchant == null) {
            return importedMerchant;
        }

        existingMerchant.updateFromImport(
                importedMerchant.getDatasetRowIdx(),
                importedMerchant.getStatus(),
                importedMerchant.getUcpUrl(),
                importedMerchant.getHttpStatus(),
                importedMerchant.getUcpVersion(),
                importedMerchant.isHasCheckout(),
                importedMerchant.isHasIdentityLinking(),
                importedMerchant.isHasCartManagement(),
                importedMerchant.isHasOrder(),
                importedMerchant.isHasPaymentToken(),
                importedMerchant.getCapabilityCount(),
                importedMerchant.getAiBotPolicies(),
                importedMerchant.getTransports(),
                importedMerchant.getLastCheckedAt(),
                importedMerchant.getLastSuccessAt(),
                importedMerchant.getFetchedAt(),
                importedMerchant.getSourceHash()
        );
        return existingMerchant;
    }

    private Integer toHttpStatus(Double value) {
        if (value == null) {
            return null;
        }
        return value.intValue();
    }

    private boolean toBoolean(Integer value) {
        return value != null && value == 1;
    }

    private String sourceHash(HuggingFaceDatasetRow datasetRow) {
        UcpMerchantDatasetRow row = datasetRow.row();
        String source = String.join("\u001F",
                stableValue(datasetRow.rowIdx()),
                stableValue(row.domain()),
                stableValue(row.status()),
                stableValue(row.ucpUrl()),
                stableValue(toHttpStatus(row.httpStatus())),
                stableValue(row.version()),
                stableValue(row.hasCheckout()),
                stableValue(row.hasIdentityLinking()),
                stableValue(row.hasCartManagement()),
                stableValue(row.hasOrder()),
                stableValue(row.hasPaymentToken()),
                stableValue(row.capabilityCount()),
                stableValue(row.aiBotPolicies()),
                stableValue(row.transports()),
                stableValue(row.lastCheckedAt()),
                stableValue(row.lastSuccessAt())
        );
        return sha256(source);
    }

    private String stableValue(Object value) {
        if (value == null) {
            return "";
        }
        return value.toString();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
