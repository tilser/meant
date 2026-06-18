package com.meant.api.module.user.controller.mapper;

import com.meant.api.module.user.controller.request.UpdateUserSettingsRequest;
import com.meant.api.module.user.controller.request.UpdateUserProfileRequest;
import com.meant.api.module.user.controller.request.SaveUserProductRequest;
import com.meant.api.module.user.service.command.SaveUserProductCommand;
import com.meant.api.module.user.service.command.UpdateUserProfileCommand;
import com.meant.api.module.user.service.command.UpdateUserSettingsCommand;
import com.meant.api.module.user.service.command.UpsertUserCommand;
import com.meant.api.module.user.service.command.UserLocationCommand;
import com.meant.api.module.user.service.dto.AuthenticatedUser;
import com.meant.api.module.user.service.dto.ParsedUserPreferenceFilters;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class UserCommandMapper {

    private UserCommandMapper() {
    }

    public static UpsertUserCommand toUpsertCommand(AuthenticatedUser authenticatedUser) {
        return new UpsertUserCommand(
                authenticatedUser.id(),
                authenticatedUser.email(),
                authenticatedUser.firstName(),
                authenticatedUser.surname());
    }

    public static UpdateUserProfileCommand toUpdateCommand(UUID userId, UpdateUserProfileRequest request) {
        return new UpdateUserProfileCommand(
                userId,
                request.firstName(),
                request.surname());
    }

    public static UpdateUserSettingsCommand toUpdateSettingsCommand(
            UUID userId,
            UpdateUserSettingsRequest request,
            ParsedUserPreferenceFilters parsedFilters
    ) {
        return new UpdateUserSettingsCommand(
                userId,
                request.budget(),
                Boolean.TRUE.equals(request.budgetUnlimited()),
                request.clothingFit(),
                request.location() == null
                        ? null
                        : new UserLocationCommand(
                                request.location().country(),
                                request.location().code(),
                                request.location().city()),
                request.locations() == null
                        ? null
                        : request.locations().stream()
                                .map(location -> new UserLocationCommand(
                                        location.country(),
                                        location.code(),
                                        location.city()))
                                .toList(),
                request.filterIds() == null ? null : new LinkedHashSet<>(request.filterIds()),
                parsedFilters == null ? Set.of() : new LinkedHashSet<>(parsedFilters.filterIds()),
                parsedFilters == null ? List.of() : parsedFilters.unmappedPreferences());
    }

    public static SaveUserProductCommand toSaveUserProductCommand(UUID userId, SaveUserProductRequest request) {
        return new SaveUserProductCommand(
                userId,
                request.id(),
                request.productHash(),
                request.name(),
                request.brand(),
                request.category(),
                request.tone(),
                request.imageUrl(),
                request.productUrl(),
                request.remote(),
                request.match(),
                request.priceFrom(),
                request.merchants(),
                request.satisfies(),
                request.misses(),
                request.note(),
                request.pros(),
                request.cons(),
                new SaveUserProductCommand.Review(
                        request.review().score(),
                        request.review().count(),
                        request.review().insight()),
                request.offers().stream()
                        .map(offer -> new SaveUserProductCommand.Offer(
                                offer.merchant(),
                                offer.price(),
                                offer.delivery(),
                                offer.merchantId(),
                                offer.merchantDomain(),
                                offer.productVariantId(),
                                offer.variantTitle(),
                                offer.available()))
                        .toList(),
                request.needs(),
                request.provides());
    }
}
