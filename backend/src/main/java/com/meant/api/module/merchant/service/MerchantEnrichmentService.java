package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.entity.MerchantCapability;
import com.meant.api.module.merchant.entity.MerchantCapabilityExtension;
import com.meant.api.module.merchant.entity.MerchantCapabilityRequirement;
import com.meant.api.module.merchant.entity.MerchantCategory;
import com.meant.api.module.merchant.entity.MerchantPaymentHandler;
import com.meant.api.module.merchant.entity.MerchantPopularSearch;
import com.meant.api.module.merchant.entity.MerchantRaw;
import com.meant.api.module.merchant.repository.MerchantCapabilityExtensionRepository;
import com.meant.api.module.merchant.repository.MerchantCapabilityRepository;
import com.meant.api.module.merchant.repository.MerchantCapabilityRequirementRepository;
import com.meant.api.module.merchant.repository.MerchantCategoryRepository;
import com.meant.api.module.merchant.repository.MerchantPaymentHandlerRepository;
import com.meant.api.module.merchant.repository.MerchantPopularSearchRepository;
import com.meant.api.module.merchant.repository.MerchantRawRepository;
import com.meant.api.module.merchant.repository.MerchantRepository;
import com.meant.api.module.merchant.repository.MerchantServiceRepository;
import com.meant.api.module.merchant.service.command.EnrichMerchantsCommand;
import com.meant.api.module.merchant.service.dto.MerchantMcpProfileResult;
import com.meant.api.module.merchant.service.dto.MerchantProfileData;
import com.meant.api.module.merchant.service.dto.UcpCapabilityDefinition;
import com.meant.api.module.merchant.service.dto.UcpPaymentHandlerDefinition;
import com.meant.api.module.merchant.service.dto.UcpProfile;
import com.meant.api.module.merchant.service.dto.UcpServiceDefinition;
import com.meant.api.module.merchant.service.dto.UcpVersionRange;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;

@Slf4j
@Service
@Validated
@RequiredArgsConstructor
public class MerchantEnrichmentService {

    private static final String SHOPPING_SERVICE_NAME = "dev.ucp.shopping";
    private static final String MCP_TRANSPORT = "mcp";
    private static final String PROCESSED_UPDATED = "PROCESSED_UPDATED";
    private static final String PROCESSED_UNCHANGED = "PROCESSED_UNCHANGED";
    private static final String FAILED_RETRYABLE = "FAILED_RETRYABLE";
    private static final int ERROR_LENGTH_LIMIT = 2_000;

    private final MerchantRawRepository merchantRawRepository;
    private final MerchantRepository merchantRepository;
    private final MerchantServiceRepository merchantServiceRepository;
    private final MerchantCapabilityRepository merchantCapabilityRepository;
    private final MerchantCapabilityExtensionRepository merchantCapabilityExtensionRepository;
    private final MerchantCapabilityRequirementRepository merchantCapabilityRequirementRepository;
    private final MerchantPaymentHandlerRepository merchantPaymentHandlerRepository;
    private final MerchantCategoryRepository merchantCategoryRepository;
    private final MerchantPopularSearchRepository merchantPopularSearchRepository;
    private final UcpProfileClient ucpProfileClient;
    private final MerchantDomainMcpClient merchantDomainMcpClient;
    private final MerchantProfileParser merchantProfileParser;
    private final MerchantProfileHashService merchantProfileHashService;
    private final TransactionTemplate transactionTemplate;

    public void enrichMerchants(@NotNull @Valid EnrichMerchantsCommand command) {
        List<MerchantRaw> merchantRows = merchantRawRepository.findUnprocessedActive(PageRequest.of(0, command.batchSize()));
        merchantRows.forEach(this::enrichMerchant);
        log.info("Merchant enrichment completed. Attempted merchants: {}", merchantRows.size());
    }

    private void enrichMerchant(MerchantRaw merchantRaw) {
        try {
            UcpProfile ucpProfile = ucpProfileClient.fetchProfile(merchantRaw.getUcpUrl());
            MerchantMcpProfileResult mcpProfile = merchantDomainMcpClient.fetchStoreProfile(merchantRaw.getDomain());
            MerchantProfileData profileData = merchantProfileParser.parse(merchantRaw.getDomain(), mcpProfile.entry());
            String profileHash = merchantProfileHashService.hash(ucpProfile, profileData);
            String advertisedMcpEndpoint = advertisedMcpEndpoint(ucpProfile);
            persistProfile(
                    merchantRaw,
                    ucpProfile,
                    mcpProfile,
                    profileData,
                    profileHash,
                    advertisedMcpEndpoint
            );
        } catch (RuntimeException exception) {
            markFailure(merchantRaw.getId(), exception);
        }
    }

    private void persistProfile(
            MerchantRaw fetchedMerchantRaw,
            UcpProfile ucpProfile,
            MerchantMcpProfileResult mcpProfile,
            MerchantProfileData profileData,
            String profileHash,
            String advertisedMcpEndpoint
    ) {
        transactionTemplate.executeWithoutResult(_ -> {
            MerchantRaw merchantRaw = merchantRawRepository.getReferenceById(fetchedMerchantRaw.getId());
            Merchant merchant = merchantRepository.findByDomain(merchantRaw.getDomain()).orElse(null);
            Instant now = Instant.now();

            if (merchant != null && profileHash.equals(merchant.getProfileHash())) {
                merchant.updateProfileMetadata(
                        merchantRaw,
                        merchantRaw.getDomain(),
                        merchantRaw.getUcpUrl(),
                        valueOrEmpty(ucpProfile.version()),
                        advertisedMcpEndpoint,
                        mcpProfile.endpoint(),
                        merchantRaw.isActive(),
                        now,
                        now
                );
                merchantRaw.markProcessed(PROCESSED_UNCHANGED, now);
                return;
            }

            if (merchant == null) {
                merchant = Merchant.builder()
                        .merchantRaw(merchantRaw)
                        .domain(merchantRaw.getDomain())
                        .ucpUrl(merchantRaw.getUcpUrl())
                        .ucpVersion(valueOrEmpty(ucpProfile.version()))
                        .advertisedMcpEndpoint(advertisedMcpEndpoint)
                        .profileMcpEndpoint(mcpProfile.endpoint())
                        .profileHash(profileHash)
                        .name(profileData.name())
                        .description(profileData.description())
                        .about(profileData.about())
                        .targetAudience(profileData.targetAudience())
                        .profileQuestion(profileData.profileQuestion())
                        .profileAnswerRaw(profileData.profileAnswerRaw())
                        .active(merchantRaw.isActive())
                        .lastProfiledAt(now)
                        .createdAt(now)
                        .updatedAt(now)
                        .build();
            } else {
                merchant.updateProfile(
                        merchantRaw,
                        merchantRaw.getDomain(),
                        merchantRaw.getUcpUrl(),
                        valueOrEmpty(ucpProfile.version()),
                        advertisedMcpEndpoint,
                        mcpProfile.endpoint(),
                        profileHash,
                        profileData.name(),
                        profileData.description(),
                        profileData.about(),
                        profileData.targetAudience(),
                        profileData.profileQuestion(),
                        profileData.profileAnswerRaw(),
                        merchantRaw.isActive(),
                        now,
                        now
                );
            }

            Merchant savedMerchant = merchantRepository.save(merchant);
            replaceChildren(savedMerchant, ucpProfile, profileData);
            merchantRaw.markProcessed(PROCESSED_UPDATED, now);
        });
    }

    private void replaceChildren(Merchant merchant, UcpProfile ucpProfile, MerchantProfileData profileData) {
        deleteChildren(merchant);
        saveServices(merchant, ucpProfile);
        saveCapabilities(merchant, ucpProfile);
        savePaymentHandlers(merchant, ucpProfile);
        saveCategories(merchant, profileData);
        savePopularSearches(merchant, profileData);
    }

    private void deleteChildren(Merchant merchant) {
        merchantCapabilityExtensionRepository.deleteByMerchant(merchant);
        merchantCapabilityRequirementRepository.deleteByMerchant(merchant);
        merchantCapabilityRepository.deleteByMerchant(merchant);
        merchantServiceRepository.deleteByMerchant(merchant);
        merchantPaymentHandlerRepository.deleteByMerchant(merchant);
        merchantCategoryRepository.deleteByMerchant(merchant);
        merchantPopularSearchRepository.deleteByMerchant(merchant);
    }

    private void saveServices(Merchant merchant, UcpProfile ucpProfile) {
        List<com.meant.api.module.merchant.entity.MerchantService> services = safeMap(ucpProfile.services()).entrySet().stream()
                .flatMap(entry -> safeList(entry.getValue()).stream()
                        .map(service -> com.meant.api.module.merchant.entity.MerchantService.builder()
                                .merchant(merchant)
                                .name(entry.getKey())
                                .transport(valueOrEmpty(service.transport()))
                                .endpoint(service.endpoint())
                                .version(valueOrEmpty(service.version()))
                                .specUrl(resourceUrl(service.spec()))
                                .schemaUrl(resourceUrl(service.schema()))
                                .build()))
                .toList();
        merchantServiceRepository.saveAll(services);
    }

    private void saveCapabilities(Merchant merchant, UcpProfile ucpProfile) {
        safeMap(ucpProfile.capabilities()).forEach((name, capabilityDefinitions) -> safeList(capabilityDefinitions).forEach(capabilityDefinition -> {
            MerchantCapability capability = merchantCapabilityRepository.save(MerchantCapability.builder()
                    .merchant(merchant)
                    .name(name)
                    .capabilityId(capabilityDefinition.id())
                    .version(valueOrEmpty(capabilityDefinition.version()))
                    .specUrl(resourceUrl(capabilityDefinition.spec()))
                    .schemaUrl(resourceUrl(capabilityDefinition.schema()))
                    .requiresProtocolMin(protocolMin(capabilityDefinition))
                    .requiresProtocolMax(protocolMax(capabilityDefinition))
                    .build());

            List<MerchantCapabilityExtension> extensions = safeList(capabilityDefinition.extendsCapabilities()).stream()
                    .map(parentCapabilityName -> MerchantCapabilityExtension.builder()
                            .merchantCapability(capability)
                            .parentCapabilityName(parentCapabilityName)
                            .build())
                    .toList();
            merchantCapabilityExtensionRepository.saveAll(extensions);

            List<MerchantCapabilityRequirement> requirements = safeMap(requiresCapabilities(capabilityDefinition)).entrySet().stream()
                    .map(entry -> MerchantCapabilityRequirement.builder()
                            .merchantCapability(capability)
                            .requiredCapabilityName(entry.getKey())
                            .minVersion(entry.getValue() == null ? null : entry.getValue().min())
                            .maxVersion(entry.getValue() == null ? null : entry.getValue().max())
                            .build())
                    .toList();
            merchantCapabilityRequirementRepository.saveAll(requirements);
        }));
    }

    private void savePaymentHandlers(Merchant merchant, UcpProfile ucpProfile) {
        List<MerchantPaymentHandler> paymentHandlers = safeMap(ucpProfile.paymentHandlers()).entrySet().stream()
                .flatMap(entry -> safeList(entry.getValue()).stream()
                        .map(paymentHandler -> MerchantPaymentHandler.builder()
                                .merchant(merchant)
                                .name(entry.getKey())
                                .handlerId(paymentHandler.id())
                                .version(valueOrEmpty(paymentHandler.version()))
                                .specUrl(resourceUrl(paymentHandler.spec()))
                                .schemaUrl(resourceUrl(paymentHandler.schema()))
                                .build()))
                .toList();
        merchantPaymentHandlerRepository.saveAll(paymentHandlers);
    }

    private void saveCategories(Merchant merchant, MerchantProfileData profileData) {
        List<MerchantCategory> categories = profileData.categories().stream()
                .collect(Collectors.toMap(
                        merchantProfileHashService::normalizeCategory,
                        Function.identity(),
                        (left, _) -> left
                ))
                .entrySet().stream()
                .map(entry -> MerchantCategory.builder()
                        .merchant(merchant)
                        .name(entry.getValue())
                        .normalizedName(entry.getKey())
                        .build())
                .toList();
        merchantCategoryRepository.saveAll(categories);
    }

    private void savePopularSearches(Merchant merchant, MerchantProfileData profileData) {
        List<MerchantPopularSearch> popularSearches = profileData.popularSearches().stream()
                .distinct()
                .map(searchText -> MerchantPopularSearch.builder()
                        .merchant(merchant)
                        .searchText(searchText)
                        .build())
                .toList();
        merchantPopularSearchRepository.saveAll(popularSearches);
    }

    private String advertisedMcpEndpoint(UcpProfile ucpProfile) {
        return safeList(safeMap(ucpProfile.services()).get(SHOPPING_SERVICE_NAME)).stream()
                .filter(service -> MCP_TRANSPORT.equalsIgnoreCase(service.transport()))
                .map(UcpServiceDefinition::endpoint)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private void markFailure(UUID merchantRawId, RuntimeException exception) {
        transactionTemplate.executeWithoutResult(_ -> {
            MerchantRaw merchantRaw = merchantRawRepository.findById(merchantRawId).orElseThrow();
            merchantRaw.markProcessingFailure(FAILED_RETRYABLE, truncate(exception.getMessage()));
        });
    }

    private String truncate(String value) {
        if (value == null || value.length() <= ERROR_LENGTH_LIMIT) {
            return value;
        }
        return value.substring(0, ERROR_LENGTH_LIMIT);
    }

    private String protocolMin(UcpCapabilityDefinition capabilityDefinition) {
        if (capabilityDefinition.requires() == null || capabilityDefinition.requires().protocol() == null) {
            return null;
        }
        return capabilityDefinition.requires().protocol().min();
    }

    private String protocolMax(UcpCapabilityDefinition capabilityDefinition) {
        if (capabilityDefinition.requires() == null || capabilityDefinition.requires().protocol() == null) {
            return null;
        }
        return capabilityDefinition.requires().protocol().max();
    }

    private Map<String, UcpVersionRange> requiresCapabilities(UcpCapabilityDefinition capabilityDefinition) {
        if (capabilityDefinition.requires() == null) {
            return Map.of();
        }
        return capabilityDefinition.requires().capabilities();
    }

    private String resourceUrl(com.meant.api.module.merchant.service.dto.UcpResourceReference reference) {
        return reference == null ? null : reference.url();
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values.stream().filter(Objects::nonNull).toList();
    }

    private <T> Map<String, T> safeMap(Map<String, T> values) {
        return values == null ? Map.of() : values;
    }
}
