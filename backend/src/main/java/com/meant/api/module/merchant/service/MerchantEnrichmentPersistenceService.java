package com.meant.api.module.merchant.service;

import static com.meant.api.common.util.CollectionUtils.safeNonNullList;

import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.entity.MerchantCapability;
import com.meant.api.module.merchant.entity.MerchantCapabilityExtension;
import com.meant.api.module.merchant.entity.MerchantCapabilityRequirement;
import com.meant.api.module.merchant.entity.MerchantCategory;
import com.meant.api.module.merchant.entity.MerchantMcpToolsList;
import com.meant.api.module.merchant.entity.MerchantPaymentHandler;
import com.meant.api.module.merchant.entity.MerchantPopularSearch;
import com.meant.api.module.merchant.entity.MerchantRaw;
import com.meant.api.module.merchant.repository.MerchantCapabilityExtensionRepository;
import com.meant.api.module.merchant.repository.MerchantCapabilityRepository;
import com.meant.api.module.merchant.repository.MerchantCapabilityRequirementRepository;
import com.meant.api.module.merchant.repository.MerchantCategoryRepository;
import com.meant.api.module.merchant.repository.MerchantMcpToolsListRepository;
import com.meant.api.module.merchant.repository.MerchantPaymentHandlerRepository;
import com.meant.api.module.merchant.repository.MerchantPopularSearchRepository;
import com.meant.api.module.merchant.repository.MerchantRawRepository;
import com.meant.api.module.merchant.repository.MerchantRepository;
import com.meant.api.module.merchant.repository.MerchantServiceRepository;
import com.meant.api.module.merchant.service.dto.MerchantMcpProfileResult;
import com.meant.api.module.merchant.service.dto.MerchantMcpToolsListResult;
import com.meant.api.module.merchant.service.dto.MerchantProfileData;
import com.meant.api.module.merchant.service.dto.UcpCapabilityDefinition;
import com.meant.api.module.merchant.service.dto.UcpProfile;
import com.meant.api.module.merchant.service.dto.UcpServiceDefinition;
import com.meant.api.module.merchant.service.dto.UcpVersionRange;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MerchantEnrichmentPersistenceService {

    private static final String PROCESSED_UPDATED = "PROCESSED_UPDATED";
    private static final String PROCESSED_UNCHANGED = "PROCESSED_UNCHANGED";

    private final MerchantRawRepository merchantRawRepository;
    private final MerchantRepository merchantRepository;
    private final MerchantServiceRepository merchantServiceRepository;
    private final MerchantCapabilityRepository merchantCapabilityRepository;
    private final MerchantCapabilityExtensionRepository merchantCapabilityExtensionRepository;
    private final MerchantCapabilityRequirementRepository merchantCapabilityRequirementRepository;
    private final MerchantMcpToolsListRepository merchantMcpToolsListRepository;
    private final MerchantPaymentHandlerRepository merchantPaymentHandlerRepository;
    private final MerchantCategoryRepository merchantCategoryRepository;
    private final MerchantPopularSearchRepository merchantPopularSearchRepository;
    private final MerchantProfileHashService merchantProfileHashService;

    @Transactional
    public void persistProfile(
            UUID merchantRawId,
            UcpProfile ucpProfile,
            String profileRaw,
            String profileEndpoint,
            Instant profileCapturedAt,
            MerchantMcpProfileResult mcpProfile,
            MerchantProfileData profileData,
            String profileHash,
            String advertisedMcpEndpoint,
            MerchantMcpToolsListResult toolsList
    ) {
        MerchantRaw merchantRaw = merchantRawRepository.getReferenceById(merchantRawId);
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
            updateProfileArchive(
                    merchant,
                    ucpProfile,
                    profileRaw,
                    profileEndpoint,
                    profileCapturedAt,
                    toolsList
            );
            persistToolsList(merchant, toolsList, now);
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
        updateProfileArchive(
                merchant,
                ucpProfile,
                profileRaw,
                profileEndpoint,
                profileCapturedAt,
                toolsList
        );

        Merchant savedMerchant = merchantRepository.save(merchant);
        persistToolsList(savedMerchant, toolsList, now);
        replaceChildren(savedMerchant, ucpProfile, profileData);
        merchantRaw.markProcessed(PROCESSED_UPDATED, now);
    }

    @Transactional
    public void markFailure(UUID merchantRawId, String status, String message) {
        MerchantRaw merchantRaw = merchantRawRepository.findById(merchantRawId).orElseThrow();
        merchantRaw.markProcessingFailure(status, message);
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
                .flatMap(entry -> safeNonNullList(entry.getValue()).stream()
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
        safeMap(ucpProfile.capabilities()).forEach((name, capabilityDefinitions) -> safeNonNullList(capabilityDefinitions).forEach(capabilityDefinition -> {
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

            List<MerchantCapabilityExtension> extensions = safeNonNullList(capabilityDefinition.extendsCapabilities()).stream()
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
                .flatMap(entry -> safeNonNullList(entry.getValue()).stream()
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

    private void updateProfileArchive(
            Merchant merchant,
            UcpProfile ucpProfile,
            String profileRaw,
            String profileEndpoint,
            Instant profileCapturedAt,
            MerchantMcpToolsListResult toolsList
    ) {
        merchant.updateProfileArchive(
                profileRaw,
                profileCapturedAt,
                profileEndpoint,
                valueOrEmpty(ucpProfile.version()),
                toolsList.agentProfileHash(),
                toolsList.toolsListHash()
        );
    }

    private void persistToolsList(Merchant merchant, MerchantMcpToolsListResult toolsList, Instant now) {
        MerchantMcpToolsList toolsListRecord = merchantMcpToolsListRepository
                .findByMerchantIdAndAgentProfileHash(merchant.getId(), toolsList.agentProfileHash())
                .orElseGet(() -> MerchantMcpToolsList.builder()
                        .merchant(merchant)
                        .agentProfileHash(toolsList.agentProfileHash())
                        .createdAt(now)
                        .build());
        toolsListRecord.updateToolsList(
                toolsList.endpoint(),
                toolsList.toolsListRaw(),
                toolsList.toolsListHash(),
                toolsList.capturedAt(),
                now
        );
        merchantMcpToolsListRepository.save(toolsListRecord);
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

    private <T> Map<String, T> safeMap(Map<String, T> values) {
        return values == null ? Map.of() : values;
    }
}
