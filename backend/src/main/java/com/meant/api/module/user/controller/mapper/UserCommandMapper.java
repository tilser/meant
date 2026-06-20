package com.meant.api.module.user.controller.mapper;

import com.meant.api.module.user.constant.UserInventoryCategory;
import com.meant.api.module.user.constant.UserInventorySource;
import com.meant.api.module.user.controller.request.AddUserInventoryItemRequest;
import com.meant.api.module.user.controller.request.AddUserInventoryPhotoRequest;
import com.meant.api.module.user.controller.request.RecordUserTasteBehaviorRequest;
import com.meant.api.module.user.controller.request.UpdateUserSettingsRequest;
import com.meant.api.module.user.controller.request.UpdateUserTasteSignalRequest;
import com.meant.api.module.user.controller.request.UpdateUserInventoryItemRequest;
import com.meant.api.module.user.controller.request.UpdateUserProfilePictureRequest;
import com.meant.api.module.user.controller.request.UpdateUserProfileRequest;
import com.meant.api.module.user.controller.request.SaveUserProductRequest;
import com.meant.api.module.user.service.command.CreateUserInventoryItemCommand;
import com.meant.api.module.user.service.command.CreateUserInventoryPhotoItemCommand;
import com.meant.api.module.user.service.command.RecordUserTasteBehaviorCommand;
import com.meant.api.module.user.service.command.SaveUserProductCommand;
import com.meant.api.module.user.service.command.UpdateUserInventoryItemCommand;
import com.meant.api.module.user.service.command.UpdateUserProfilePictureCommand;
import com.meant.api.module.user.service.command.UpdateUserProfileCommand;
import com.meant.api.module.user.service.command.UpdateUserSettingsCommand;
import com.meant.api.module.user.service.command.UpdateUserTasteSignalCommand;
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

    public static UpdateUserProfilePictureCommand toUpdateCommand(
            UUID userId,
            UpdateUserProfilePictureRequest request
    ) {
        return new UpdateUserProfilePictureCommand(
                userId,
                request.profilePicturePath());
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

    public static RecordUserTasteBehaviorCommand toRecordUserTasteBehaviorCommand(
            UUID userId,
            RecordUserTasteBehaviorRequest request
    ) {
        return new RecordUserTasteBehaviorCommand(
                userId,
                request.behavior(),
                toSaveUserProductCommand(userId, request.product())
        );
    }

    public static UpdateUserTasteSignalCommand toUpdateUserTasteSignalCommand(
            UUID userId,
            UUID signalId,
            UpdateUserTasteSignalRequest request
    ) {
        return new UpdateUserTasteSignalCommand(
                userId,
                signalId,
                request.weight(),
                request.disabled()
        );
    }

    public static CreateUserInventoryItemCommand toCreateInventoryItemCommand(
            UUID userId,
            AddUserInventoryItemRequest request
    ) {
        return new CreateUserInventoryItemCommand(
                userId,
                UserInventorySource.MANUAL,
                null,
                null,
                request.name(),
                request.brand(),
                request.category() == null ? UserInventoryCategory.OTHER : request.category(),
                request.description(),
                request.imageUrl(),
                request.productUrl(),
                null,
                request.quantity(),
                request.unit(),
                request.location(),
                request.notes(),
                request.attributes(),
                request.consumable(),
                request.restockEnabled(),
                request.restockThreshold(),
                null);
    }

    public static CreateUserInventoryPhotoItemCommand toCreateInventoryPhotoItemCommand(
            UUID userId,
            AddUserInventoryPhotoRequest request
    ) {
        return new CreateUserInventoryPhotoItemCommand(
                userId,
                request.photoUrl(),
                request.name(),
                request.brand(),
                request.category(),
                request.description(),
                request.quantity(),
                request.unit(),
                request.location(),
                request.notes(),
                request.attributes(),
                request.consumable(),
                request.restockEnabled(),
                request.restockThreshold());
    }

    public static UpdateUserInventoryItemCommand toUpdateInventoryItemCommand(
            UUID userId,
            UUID itemId,
            UpdateUserInventoryItemRequest request
    ) {
        return new UpdateUserInventoryItemCommand(
                userId,
                itemId,
                request.name(),
                request.brand(),
                request.category(),
                request.description(),
                request.imageUrl(),
                request.productUrl(),
                request.photoUrl(),
                request.quantity(),
                request.unit(),
                request.location(),
                request.notes(),
                request.attributes(),
                request.consumable(),
                request.restockEnabled(),
                request.restockThreshold());
    }
}
