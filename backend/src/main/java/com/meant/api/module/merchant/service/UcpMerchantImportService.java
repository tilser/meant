package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.constant.MerchantRawSource;
import com.meant.api.module.merchant.entity.MerchantRaw;
import com.meant.api.module.merchant.properties.MerchantImportProperties;
import com.meant.api.module.merchant.service.dto.HuggingFaceDatasetRow;
import com.meant.api.module.merchant.service.dto.UcpMerchantDatasetRow;
import java.net.IDN;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Slf4j
@Service
@Validated
@RequiredArgsConstructor
public class UcpMerchantImportService {

    private static final String VERIFIED_STATUS = "verified";

    private final UcpDatasetClient ucpDatasetClient;
    private final MerchantImportProperties merchantImportProperties;
    private final UcpMerchantImportPersistenceService ucpMerchantImportPersistenceService;

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

        ucpMerchantImportPersistenceService.saveImport(verifiedMerchants, fetchedAt);

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
                        datasetRow -> normalizeDomain(datasetRow.row().domain()),
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
        String normalizedDomain = IDN.toASCII(domain.trim(), IDN.USE_STD3_ASCII_RULES)
                .toLowerCase(Locale.ROOT);
        while (normalizedDomain.endsWith(".")) {
            normalizedDomain = normalizedDomain.substring(0, normalizedDomain.length() - 1);
        }
        if (normalizedDomain.startsWith("www.")) {
            return normalizedDomain.substring("www.".length());
        }
        return normalizedDomain;
    }

    private MerchantRaw toMerchantRaw(HuggingFaceDatasetRow datasetRow, Instant fetchedAt) {
        UcpMerchantDatasetRow row = datasetRow.row();

        return MerchantRaw.builder()
                .source(MerchantRawSource.HUGGING_FACE)
                .datasetRowIdx(datasetRow.rowIdx())
                .domain(normalizeDomain(row.domain()))
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
