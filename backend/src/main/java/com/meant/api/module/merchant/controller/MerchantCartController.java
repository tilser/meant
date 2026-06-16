package com.meant.api.module.merchant.controller;

import com.meant.api.module.merchant.controller.request.MerchantCartCreateRequest;
import com.meant.api.module.merchant.controller.request.MerchantCartUpdateRequest;
import com.meant.api.module.merchant.controller.mapper.MerchantCartCommandMapper;
import com.meant.api.module.merchant.controller.response.MerchantCartResponse;
import com.meant.api.module.merchant.controller.response.MerchantCheckoutResponse;
import com.meant.api.module.merchant.service.MerchantCartService;
import com.meant.api.module.merchant.service.query.GetMerchantCartQuery;
import com.meant.api.module.merchant.service.query.GetMerchantCheckoutQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/merchant-carts")
@RequiredArgsConstructor
@Tag(name = "Merchant Carts", description = "Testing endpoints for MCP-backed merchant carts")
public class MerchantCartController {

    private final MerchantCartService merchantCartService;

    @PostMapping
    @Operation(
            summary = "Create merchant cart",
            description = "Creates a remote MCP cart with initial items and stores the local cart snapshot."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Created merchant cart snapshot",
            content = @Content(schema = @Schema(implementation = MerchantCartResponse.class))
    )
    public MerchantCartResponse create(@Valid @RequestBody MerchantCartCreateRequest request) {
        return MerchantCartResponse.from(merchantCartService.create(MerchantCartCommandMapper.toCommand(request)));
    }

    @GetMapping("/{cartId}")
    @Operation(
            summary = "Get merchant cart",
            description = "Returns the local cart snapshot, or refreshes it from MCP when refresh is true."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Merchant cart snapshot",
            content = @Content(schema = @Schema(implementation = MerchantCartResponse.class))
    )
    public MerchantCartResponse get(
            @Parameter(description = "Local merchant cart UUID.", required = true)
            @PathVariable UUID cartId,
            @Parameter(description = "Refresh the local snapshot from the remote MCP cart before returning it.")
            @RequestParam(defaultValue = "false") boolean refresh
    ) {
        return MerchantCartResponse.from(merchantCartService.get(new GetMerchantCartQuery(cartId, refresh)));
    }

    @PatchMapping("/{cartId}")
    @Operation(
            summary = "Update merchant cart",
            description = "Adds items, updates line quantities, removes lines, and stores the refreshed remote cart snapshot."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Updated merchant cart snapshot",
            content = @Content(schema = @Schema(implementation = MerchantCartResponse.class))
    )
    public MerchantCartResponse update(
            @Parameter(description = "Local merchant cart UUID.", required = true)
            @PathVariable UUID cartId,
            @Valid @RequestBody MerchantCartUpdateRequest request
    ) {
        return MerchantCartResponse.from(
                merchantCartService.update(MerchantCartCommandMapper.toCommand(cartId, request))
        );
    }

    @GetMapping("/{cartId}/checkout")
    @Operation(
            summary = "Get merchant cart checkout URL",
            description = "Returns the stored checkout URL, or refreshes the cart from MCP when missing or requested."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Merchant cart checkout URL",
            content = @Content(schema = @Schema(implementation = MerchantCheckoutResponse.class))
    )
    public MerchantCheckoutResponse checkout(
            @Parameter(description = "Local merchant cart UUID.", required = true)
            @PathVariable UUID cartId,
            @Parameter(description = "Refresh the local snapshot from the remote MCP cart before returning checkout.")
            @RequestParam(defaultValue = "false") boolean refresh
    ) {
        return MerchantCheckoutResponse.from(
                merchantCartService.checkout(new GetMerchantCheckoutQuery(cartId, refresh))
        );
    }

}
