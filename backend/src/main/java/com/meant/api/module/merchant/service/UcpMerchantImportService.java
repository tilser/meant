package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.entity.MerchantRaw;
import com.meant.api.module.merchant.repository.MerchantRawRepository;
import com.meant.api.module.merchant.service.command.ImportUcpMerchantsCommand;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@Validated
public class UcpMerchantImportService {

    private static final String VERIFIED_STATUS = "verified";

    private final UcpDatasetClient ucpDatasetClient;
    private final MerchantRawRepository merchantRawRepository;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public UcpMerchantImportService(
            UcpDatasetClient ucpDatasetClient,
            MerchantRawRepository merchantRawRepository,
            TransactionTemplate transactionTemplate,
            ObjectMapper objectMapper
    ) {
        this.ucpDatasetClient = ucpDatasetClient;
        this.merchantRawRepository = merchantRawRepository;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
    }

    public UcpMerchantImportResult importMerchants(@Valid ImportUcpMerchantsCommand command) {
        List<HuggingFaceDatasetRow> datasetRows = ucpDatasetClient.fetchAllRows();
        OffsetDateTime fetchedAt = OffsetDateTime.now(ZoneOffset.UTC);
        List<MerchantRaw> verifiedMerchants = datasetRows.stream()
                .filter(datasetRow -> isVerified(datasetRow.row()))
                .map(datasetRow -> toMerchantRaw(datasetRow, fetchedAt))
                .toList();

        transactionTemplate.executeWithoutResult(status -> {
            merchantRawRepository.deleteAllInBatch();
            merchantRawRepository.saveAll(verifiedMerchants);
        });

        return new UcpMerchantImportResult(datasetRows.size(), verifiedMerchants.size());
    }

    private boolean isVerified(UcpMerchantDatasetRow row) {
        return row != null && VERIFIED_STATUS.equals(row.status());
    }

    private MerchantRaw toMerchantRaw(HuggingFaceDatasetRow datasetRow, OffsetDateTime fetchedAt) {
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
            throw new IllegalArgumentException("Unable to parse UCP AI bot policies", ex);
        }
    }

    private UcpTransports parseTransports(String value) {
        try {
            return new UcpTransports(objectMapper.readerForListOf(String.class).readValue(value));
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("Unable to parse UCP transports", ex);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("Unable to write UCP merchant JSON", ex);
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
