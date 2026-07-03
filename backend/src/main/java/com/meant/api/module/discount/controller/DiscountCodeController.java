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
                request.buyerIdentity(),
                request.deliveryAddressesToAdd(),
                request.deliveryAddressesToReplace(),
                request.selectedDeliveryOptions()
        );
    }
}
