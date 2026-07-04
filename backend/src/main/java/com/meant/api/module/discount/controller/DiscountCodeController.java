package com.meant.api.module.discount.controller;

import com.meant.api.module.discount.controller.request.SearchDiscountCodesRequest;
import com.meant.api.module.discount.controller.response.DiscountCodeSearchResponse;
import com.meant.api.module.discount.service.DiscountCodeSearchService;
import com.meant.api.module.discount.service.command.SearchDiscountCodesCommand;
import com.meant.api.module.user.controller.mapper.UserCommandMapper;
import com.meant.api.module.user.service.UserService;
import com.meant.api.module.user.service.dto.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/discounts")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Discounts", description = "On-demand merchant discount code discovery and validation")
public class DiscountCodeController {

    private final DiscountCodeSearchService discountCodeSearchService;
    private final UserService userService;

    @PostMapping("/search")
    @Operation(
            summary = "Search valid merchant discount codes",
            description = "Finds candidate discount codes with web search and validates them against a temporary merchant cart."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Validated merchant discount codes",
            content = @Content(schema = @Schema(implementation = DiscountCodeSearchResponse.class))
    )
    public DiscountCodeSearchResponse search(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SearchDiscountCodesRequest request
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        userService.upsert(UserCommandMapper.toUpsertCommand(authenticatedUser));
        return DiscountCodeSearchResponse.from(discountCodeSearchService.search(toCommand(authenticatedUser.id(), request)));
    }

    private SearchDiscountCodesCommand toCommand(UUID userId, SearchDiscountCodesRequest request) {
        return new SearchDiscountCodesCommand(
                userId,
                request.merchantId(),
                request.merchantDomain(),
                request.items().stream()
                        .map(item -> new SearchDiscountCodesCommand.Item(
                                item.productVariantId(),
                                item.quantity()
                        ))
                        .toList(),
                buyerIdentity(request.buyerIdentity()),
                deliveryAddresses(request.deliveryAddressesToAdd()),
                deliveryAddresses(request.deliveryAddressesToReplace()),
                deliveryOptions(request.selectedDeliveryOptions())
        );
    }

    private SearchDiscountCodesCommand.BuyerIdentity buyerIdentity(
            SearchDiscountCodesRequest.BuyerIdentity request
    ) {
        if (request == null) {
            return null;
        }
        return new SearchDiscountCodesCommand.BuyerIdentity(
                request.email(),
                request.phoneNumber(),
                request.firstName(),
                request.lastName(),
                request.countryCode()
        );
    }

    private List<SearchDiscountCodesCommand.DeliveryAddressSelection> deliveryAddresses(
            List<SearchDiscountCodesRequest.DeliveryAddressSelection> requests
    ) {
        if (requests == null) {
            return List.of();
        }
        return requests.stream()
                .map(this::deliveryAddress)
                .toList();
    }

    private SearchDiscountCodesCommand.DeliveryAddressSelection deliveryAddress(
            SearchDiscountCodesRequest.DeliveryAddressSelection request
    ) {
        if (request == null) {
            return null;
        }
        return new SearchDiscountCodesCommand.DeliveryAddressSelection(
                request.id(),
                request.selected(),
                deliveryAddress(request.deliveryAddress()),
                request.firstName(),
                request.lastName(),
                request.phoneNumber(),
                request.streetAddress(),
                request.extendedAddress(),
                request.city(),
                request.provinceCode(),
                request.postalCode(),
                request.countryCode()
        );
    }

    private SearchDiscountCodesCommand.DeliveryAddress deliveryAddress(
            SearchDiscountCodesRequest.DeliveryAddress request
    ) {
        if (request == null) {
            return null;
        }
        return new SearchDiscountCodesCommand.DeliveryAddress(
                request.firstName(),
                request.lastName(),
                request.phoneNumber(),
                request.streetAddress(),
                request.extendedAddress(),
                request.city(),
                request.provinceCode(),
                request.postalCode(),
                request.countryCode()
        );
    }

    private List<SearchDiscountCodesCommand.DeliveryOptionSelection> deliveryOptions(
            List<SearchDiscountCodesRequest.DeliveryOptionSelection> requests
    ) {
        if (requests == null) {
            return List.of();
        }
        return requests.stream()
                .map(this::deliveryOption)
                .toList();
    }

    private SearchDiscountCodesCommand.DeliveryOptionSelection deliveryOption(
            SearchDiscountCodesRequest.DeliveryOptionSelection request
    ) {
        if (request == null) {
            return null;
        }
        return new SearchDiscountCodesCommand.DeliveryOptionSelection(
                request.id(),
                request.groupId(),
                request.deliveryGroupId(),
                request.optionHandle(),
                request.deliveryOptionHandle(),
                request.selectedOptionId()
        );
    }
}
