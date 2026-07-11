package com.meant.api.module.user.service;

import com.meant.api.module.user.entity.UserSavedProduct;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.properties.UserCollectionProperties;
import com.meant.api.module.user.repository.UserSavedProductRepository;
import com.meant.api.module.user.service.command.RemoveSavedProductCommand;
import com.meant.api.module.user.service.command.SaveUserProductCommand;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.dto.UserSavedProductResult;
import com.meant.api.module.user.service.query.ListSavedProductsQuery;
import com.meant.api.plugin.catalog.common.dto.CatalogPayloadClass;
import com.meant.api.plugin.catalog.common.dto.CatalogProductReference;
import com.meant.api.plugin.catalog.common.dto.CatalogProductRehydrationResult;
import com.meant.api.plugin.catalog.common.dto.CatalogRehydrationContext;
import com.meant.api.plugin.catalog.common.dto.CatalogRehydrationStatus;
import com.meant.api.plugin.catalog.common.dto.CatalogRetentionDecision;
import com.meant.api.plugin.catalog.common.dto.CatalogRetentionMode;
import com.meant.api.plugin.catalog.common.service.CatalogDataUsePolicyResolver;
import com.meant.api.plugin.catalog.common.service.CatalogProductRehydrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@Validated
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class UserSavedProductService {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<UserSavedProductResult.Offer>> OFFER_LIST_TYPE = new TypeReference<>() {
    };

    private final UserService userService;
    private final UserSavedProductRepository userSavedProductRepository;
    private final UserTasteProfileService userTasteProfileService;
    private final UserCollectionProperties userCollectionProperties;
    private final ObjectMapper objectMapper;
    private final UserSavedProductReferenceResolver referenceResolver;
    private final CatalogDataUsePolicyResolver policyResolver;
    private final CatalogProductRehydrationService rehydrationService;
    private final UserSavedProductPersistenceService persistenceService;

    protected UserSavedProductService(
            UserService userService,
            UserSavedProductRepository userSavedProductRepository,
            UserTasteProfileService userTasteProfileService,
            UserCollectionProperties userCollectionProperties,
            ObjectMapper objectMapper
    ) {
        this(userService, userSavedProductRepository, userTasteProfileService, userCollectionProperties, objectMapper,
                null, null, null, null);
    }

    @Transactional
    public List<UserSavedProductResult> list(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid ListSavedProductsQuery query
    ) {
        validateUser(profileCommand, query.userId());
        userService.ensureProfile(profileCommand);
        return userSavedProductRepository.findByUserIdOrderByCreatedAtDesc(
                        query.userId(),
                        PageRequest.of(query.page(), boundedLimit(
                                query.limit(),
                                userCollectionProperties.savedProducts().maxLimit())))
                .stream()
                .map(this::toResult)
                .toList();
    }

    public UserSavedProductResult save(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid SaveUserProductCommand command
    ) {
        validateUser(profileCommand, command.userId());
        userService.ensureProfile(profileCommand);
        Instant now = Instant.now();
        CatalogProductReference reference = referenceResolver.resolve(command, now);
        CatalogRetentionDecision policy = policyResolver.resolve(
                reference.discoverySource(),
                CatalogPayloadClass.SAVED_INTERACTION
        );
        if (policy.mode() != CatalogRetentionMode.DURABLE_IDENTIFIERS_ONLY) {
            throw new UserException("Provider policy does not permit a durable saved-product reference");
        }
        CatalogProductRehydrationResult rehydrated = rehydrationService.rehydrate(
                reference,
                new CatalogRehydrationContext(null, null)
        );
        if (rehydrated.status() != CatalogRehydrationStatus.FRESH) {
            throw new UserException("Saved product could not be rehydrated from current provider facts");
        }
        UserSavedProduct savedProduct = persistenceService.save(command, reference, policy.policyKey(), now);
        UserSavedProductResult result = toResult(savedProduct);
        userTasteProfileService.recordSavedProduct(command.userId(), command, now);
        return result;
    }

    private int boundedLimit(int limit, int maxLimit) {
        return Math.min(limit, maxLimit);
    }

    @Transactional
    public void remove(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid RemoveSavedProductCommand command
    ) {
        validateUser(profileCommand, command.userId());
        userService.ensureProfile(profileCommand);
        userSavedProductRepository.deleteByUserIdAndProductKey(command.userId(), command.productKey());
    }

    private UserSavedProductResult toResult(UserSavedProduct entity) {
        return new UserSavedProductResult(
                entity.getProductKey(),
                entity.getProductHash(),
                entity.getName(),
                entity.getBrand(),
                entity.getCategory(),
                entity.getTone(),
                entity.getImageUrl(),
                entity.getProductUrl(),
                entity.isRemote(),
                entity.getMatchScore(),
                entity.getPriceFrom() == null ? 0.0d : entity.getPriceFrom(),
                entity.getMerchantCount(),
                fromJson(entity.getSatisfies(), STRING_LIST_TYPE, List.<String>of()),
                fromJson(entity.getMisses(), STRING_LIST_TYPE, List.<String>of()),
                entity.getNote(),
                fromJson(entity.getPros(), STRING_LIST_TYPE, List.<String>of()),
                fromJson(entity.getCons(), STRING_LIST_TYPE, List.<String>of()),
                new UserSavedProductResult.Review(
                        entity.getReviewScore(),
                        entity.getReviewCount(),
                        entity.getReviewInsight()
                ),
                fromJson(entity.getOffers(), OFFER_LIST_TYPE, List.<UserSavedProductResult.Offer>of()),
                entity.getNeeds(),
                fromJson(entity.getProvides(), STRING_LIST_TYPE, List.<String>of()),
                false,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private void validateUser(EnsureUserProfileCommand profileCommand, UUID userId) {
        if (!profileCommand.id().equals(userId)) {
            throw UserException.forbidden("Saved product user does not match authenticated user");
        }
    }

    private <T> T fromJson(String value, TypeReference<T> type, T defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return objectMapper.readValue(value, type);
        } catch (JacksonException exception) {
            throw new UserException("Could not parse saved product snapshot", exception);
        }
    }
}
