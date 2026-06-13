package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.entity.MerchantRaw;
import com.meant.api.module.merchant.exception.UcpAiBotPoliciesParsingException;
import com.meant.api.module.merchant.exception.UcpMerchantJsonWritingException;
import com.meant.api.module.merchant.exception.UcpTransportsParsingException;
import com.meant.api.module.merchant.repository.MerchantRawRepository;
import com.meant.api.module.merchant.service.dto.HuggingFaceDatasetRow;
import com.meant.api.module.merchant.service.dto.UcpAiBotPolicies;
import com.meant.api.module.merchant.service.dto.UcpMerchantDatasetRow;
import com.meant.api.module.merchant.service.dto.UcpTransports;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@Validated
@RequiredArgsConstructor
public class UcpMerchantImportService {

    private static final String VERIFIED_STATUS = "verified";

    private final UcpDatasetClient ucpDatasetClient;
    private final MerchantRawRepository merchantRawRepository;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

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
        UcpAiBotPolicies aiBotPolicies = parseAiBotPolicies(row.aiBotPolicies());
        UcpTransports transports = parseTransports(row.transports());

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
                .aiBotPolicies(writeJson(aiBotPolicies))
                .transports(writeJson(transports.names()))
                .lastCheckedAt(row.lastCheckedAt())
                .lastSuccessAt(row.lastSuccessAt())
                .fetchedAt(fetchedAt)
                .build();
    }

    private UcpAiBotPolicies parseAiBotPolicies(String value) {
        try {
            return objectMapper.readValue(value, UcpAiBotPolicies.class);
        } catch (JacksonException ex) {
            throw new UcpAiBotPoliciesParsingException(ex);
        }
    }

    private UcpTransports parseTransports(String value) {
        try {
            return new UcpTransports(objectMapper.readerForListOf(String.class).readValue(value));
        } catch (JacksonException ex) {
            throw new UcpTransportsParsingException(ex);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new UcpMerchantJsonWritingException(ex);
        }
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
