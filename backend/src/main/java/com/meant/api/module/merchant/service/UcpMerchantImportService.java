package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.entity.MerchantRaw;
import com.meant.api.module.merchant.repository.MerchantRawRepository;
import com.meant.api.module.merchant.service.dto.HuggingFaceDatasetRow;
import com.meant.api.module.merchant.service.dto.UcpMerchantDatasetRow;
import java.time.Instant;
import java.util.List;
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
    private final MerchantRawRepository merchantRawRepository;
    private final TransactionTemplate transactionTemplate;

    public void importMerchants() {
        List<HuggingFaceDatasetRow> datasetRows = ucpDatasetClient.fetchAllRows();
        Instant fetchedAt = Instant.now();
        List<MerchantRaw> verifiedMerchants = datasetRows.stream()
                .filter(datasetRow -> isVerified(datasetRow.row()))
                .map(datasetRow -> toMerchantRaw(datasetRow, fetchedAt))
                .toList();

        transactionTemplate.executeWithoutResult(_ -> {
            merchantRawRepository.deleteAllInBatch();
            merchantRawRepository.saveAll(verifiedMerchants);
        });

        log.info("Merchant import completed. Saved merchants: {}", verifiedMerchants.size());
    }

    private boolean isVerified(UcpMerchantDatasetRow row) {
        return row != null && VERIFIED_STATUS.equalsIgnoreCase(row.status().trim());
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
                .build();
    }

    private Integer toHttpStatus(Double value) {
        if (value == null) {
            return null;
        }
        return Math.toIntExact(Math.round(value));
    }

    private boolean toBoolean(Integer value) {
        return value != null && value == 1;
    }
}
