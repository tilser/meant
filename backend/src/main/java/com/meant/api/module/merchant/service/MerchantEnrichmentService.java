package com.meant.api.module.merchant.service;

import static com.meant.api.common.util.CollectionUtils.safeNonNullList;

import com.meant.api.module.merchant.entity.MerchantRaw;
import com.meant.api.module.merchant.repository.MerchantRawRepository;
import com.meant.api.module.merchant.service.command.EnrichMerchantsCommand;
import com.meant.api.module.merchant.service.dto.MerchantMcpProfileResult;
import com.meant.api.module.merchant.service.dto.MerchantMcpToolsListFetchResult;
import com.meant.api.module.merchant.service.dto.MerchantMcpToolsListResult;
import com.meant.api.module.merchant.service.dto.MerchantProfileData;
import com.meant.api.module.merchant.service.dto.UcpProfile;
import com.meant.api.module.merchant.service.dto.UcpProfileFetchResult;
import com.meant.api.module.merchant.service.dto.UcpServiceDefinition;
import com.meant.api.plugin.transport.profile.AgentProfileHashProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Slf4j
@Service
@Validated
@RequiredArgsConstructor
public class MerchantEnrichmentService {

    private static final String SHOPPING_SERVICE_NAME = "dev.ucp.shopping";
    private static final String MCP_TRANSPORT = "mcp";
    private static final String FAILED_RETRYABLE = "FAILED_RETRYABLE";
    private static final int ERROR_LENGTH_LIMIT = 2_000;

    private final MerchantRawRepository merchantRawRepository;
    private final UcpProfileClient ucpProfileClient;
    private final MerchantDomainMcpClient merchantDomainMcpClient;
    private final MerchantMcpToolClient merchantMcpToolClient;
    private final MerchantProfileParser merchantProfileParser;
    private final MerchantProfileHashService merchantProfileHashService;
    private final MerchantMcpToolsListHashService merchantMcpToolsListHashService;
    private final AgentProfileHashProvider agentProfileHashProvider;
    private final MerchantEnrichmentPersistenceService merchantEnrichmentPersistenceService;

    public void enrichMerchants(@NotNull @Valid EnrichMerchantsCommand command) {
        List<MerchantRaw> merchantRows = merchantRawRepository.findUnprocessedActive(PageRequest.of(0, command.batchSize()));
        merchantRows.forEach(this::enrichMerchant);
        log.info("Merchant enrichment completed. Attempted merchants: {}", merchantRows.size());
    }

    private void enrichMerchant(MerchantRaw merchantRaw) {
        try {
            UcpProfileFetchResult profileResult = ucpProfileClient.fetchProfileResult(
                    merchantRaw.getDomain(),
                    merchantRaw.getUcpUrl()
            );
            UcpProfile ucpProfile = profileResult.profile();
            MerchantMcpProfileResult mcpProfile = merchantDomainMcpClient.fetchStoreProfile(merchantRaw.getDomain());
            MerchantProfileData profileData = merchantProfileParser.parse(merchantRaw.getDomain(), mcpProfile.entry());
            String profileHash = merchantProfileHashService.hash(ucpProfile, profileData);
            String advertisedMcpEndpoint = advertisedMcpEndpoint(ucpProfile);
            MerchantMcpToolsListResult toolsList = fetchToolsList(
                    merchantRaw.getDomain(),
                    advertisedMcpEndpoint,
                    mcpProfile.endpoint()
            );
            persistProfile(
                    merchantRaw,
                    profileResult,
                    mcpProfile,
                    profileData,
                    profileHash,
                    advertisedMcpEndpoint,
                    toolsList
            );
        } catch (RuntimeException exception) {
            markFailure(merchantRaw.getId(), exception);
        }
    }

    private void persistProfile(
            MerchantRaw fetchedMerchantRaw,
            UcpProfileFetchResult profileResult,
            MerchantMcpProfileResult mcpProfile,
            MerchantProfileData profileData,
            String profileHash,
            String advertisedMcpEndpoint,
            MerchantMcpToolsListResult toolsList
    ) {
        merchantEnrichmentPersistenceService.persistProfile(
                fetchedMerchantRaw.getId(),
                profileResult.profile(),
                profileResult.rawProfile(),
                profileResult.endpoint(),
                profileResult.capturedAt(),
                mcpProfile,
                profileData,
                profileHash,
                advertisedMcpEndpoint,
                toolsList
        );
    }

    private MerchantMcpToolsListResult fetchToolsList(
            String domain,
            String advertisedMcpEndpoint,
            String profileMcpEndpoint
    ) {
        MerchantMcpToolsListFetchResult fetchResult = merchantMcpToolClient.listTools(
                domain,
                advertisedMcpEndpoint,
                profileMcpEndpoint
        );
        return new MerchantMcpToolsListResult(
                fetchResult.endpoint(),
                fetchResult.toolsListRaw(),
                merchantMcpToolsListHashService.hash(fetchResult.toolsListRaw()),
                agentProfileHashProvider.currentHash(),
                Instant.now()
        );
    }

    private String advertisedMcpEndpoint(UcpProfile ucpProfile) {
        return safeNonNullList(safeMap(ucpProfile.services()).get(SHOPPING_SERVICE_NAME)).stream()
                .filter(service -> MCP_TRANSPORT.equalsIgnoreCase(service.transport()))
                .map(UcpServiceDefinition::endpoint)
                .filter(endpoint -> endpoint != null && !endpoint.isBlank())
                .findFirst()
                .orElse(null);
    }

    private void markFailure(UUID merchantRawId, RuntimeException exception) {
        merchantEnrichmentPersistenceService.markFailure(
                merchantRawId,
                FAILED_RETRYABLE,
                truncate(exception.getMessage())
        );
    }

    private String truncate(String value) {
        if (value == null || value.length() <= ERROR_LENGTH_LIMIT) {
            return value;
        }
        return value.substring(0, ERROR_LENGTH_LIMIT);
    }

    private <T> Map<String, T> safeMap(Map<String, T> values) {
        return values == null ? Map.of() : values;
    }
}
