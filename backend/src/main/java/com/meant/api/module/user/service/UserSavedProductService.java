package com.meant.api.module.user.service;

import static com.meant.api.common.util.CollectionUtils.safeList;

import com.meant.api.module.user.entity.UserSavedProduct;
import com.meant.api.module.user.entity.UserSavedProduct.SavedProductSnapshot;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.repository.UserSavedProductRepository;
import com.meant.api.module.user.service.command.RemoveSavedProductCommand;
import com.meant.api.module.user.service.command.SaveUserProductCommand;
import com.meant.api.module.user.service.command.UpsertUserCommand;
import com.meant.api.module.user.service.dto.UserSavedProductResult;
import com.meant.api.module.user.service.query.ListSavedProductsQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@Validated
@RequiredArgsConstructor
public class UserSavedProductService {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<UserSavedProductResult.Offer>> OFFER_LIST_TYPE = new TypeReference<>() {
    };

    private final UserService userService;
    private final UserSavedProductRepository userSavedProductRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public List<UserSavedProductResult> list(
            @NotNull @Valid UpsertUserCommand upsertCommand,
            @NotNull @Valid ListSavedProductsQuery query
    ) {
        validateUser(upsertCommand, query.userId());
        userService.upsert(upsertCommand);
        return userSavedProductRepository.findByUserIdOrderByCreatedAtDesc(query.userId()).stream()
                .map(this::toResult)
                .toList();
    }

    @Transactional
    public UserSavedProductResult save(
            @NotNull @Valid UpsertUserCommand upsertCommand,
            @NotNull @Valid SaveUserProductCommand command
    ) {
        validateUser(upsertCommand, command.userId());
        userService.upsert(upsertCommand);
        Instant now = Instant.now();
        SavedProductSnapshot snapshot = snapshot(command);
        UserSavedProduct savedProduct = userSavedProductRepository
                .findByUserIdAndProductKey(command.userId(), command.productKey())
                .map(existing -> existing.replaceSnapshot(snapshot, now))
                .orElseGet(() -> UserSavedProduct.create(command.userId(), snapshot, now));
        return toResult(userSavedProductRepository.save(savedProduct));
    }

    @Transactional
    public void remove(
            @NotNull @Valid UpsertUserCommand upsertCommand,
            @NotNull @Valid RemoveSavedProductCommand command
    ) {
        validateUser(upsertCommand, command.userId());
        userService.upsert(upsertCommand);
        userSavedProductRepository.deleteByUserIdAndProductKey(command.userId(), command.productKey());
    }

    private SavedProductSnapshot snapshot(SaveUserProductCommand command) {
        return new SavedProductSnapshot(
                command.productKey(),
                blankToNull(command.productHash()),
                command.name(),
                command.brand(),
                command.category(),
                command.tone(),
                blankToNull(command.imageUrl()),
                blankToNull(command.productUrl()),
                command.remote(),
                command.matchScore(),
                command.priceFrom(),
                command.merchantCount(),
                toJson(safeList(command.satisfies())),
                toJson(safeList(command.misses())),
                command.note(),
                toJson(safeList(command.pros())),
                toJson(safeList(command.cons())),
                command.review().score(),
                command.review().count(),
                command.review().insight(),
                toJson(safeList(command.offers()).stream()
                        .map(offer -> new UserSavedProductResult.Offer(
                                offer.merchant(),
                                offer.price(),
                                offer.delivery(),
                                blankToNull(offer.merchantId()),
                                blankToNull(offer.merchantDomain()),
                                blankToNull(offer.productVariantId()),
                                blankToNull(offer.variantTitle()),
                                offer.available()
                        ))
                        .toList()),
                blankToNull(command.needs()),
                toJson(safeList(command.provides()))
        );
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
                entity.getPriceFrom(),
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
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private void validateUser(UpsertUserCommand upsertCommand, UUID userId) {
        if (!upsertCommand.id().equals(userId)) {
            throw UserException.forbidden("Saved product user does not match authenticated user");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new UserException("Could not serialize saved product snapshot", exception);
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

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
