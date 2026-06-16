package com.meant.api.module.cart.controller;

import com.meant.api.module.cart.controller.mapper.CartCommandMapper;
import com.meant.api.module.cart.controller.request.CartCreateRequest;
import com.meant.api.module.cart.controller.request.CartUpdateRequest;
import com.meant.api.module.cart.controller.response.CartResponse;
import com.meant.api.module.cart.controller.response.CheckoutResponse;
import com.meant.api.module.cart.service.CartService;
import com.meant.api.module.cart.service.query.GetCartQuery;
import com.meant.api.module.cart.service.query.GetCheckoutQuery;
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
@RequestMapping("/api/carts")
@RequiredArgsConstructor
@Tag(name = "Carts", description = "Testing endpoints for MCP-backed carts")
public class CartController {

    private final CartService cartService;

    @PostMapping
    @Operation(
            summary = "Create cart",
            description = "Creates a remote MCP cart with initial items and stores the local cart snapshot."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Created cart snapshot",
            content = @Content(schema = @Schema(implementation = CartResponse.class))
    )
    public CartResponse create(@Valid @RequestBody CartCreateRequest request) {
        return CartResponse.from(cartService.create(CartCommandMapper.toCommand(request)));
    }

    @GetMapping("/{cartId}")
    @Operation(
            summary = "Get cart",
            description = "Returns the local cart snapshot, or refreshes it from MCP when refresh is true."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Cart snapshot",
            content = @Content(schema = @Schema(implementation = CartResponse.class))
    )
    public CartResponse get(
            @Parameter(description = "Local cart UUID.", required = true)
            @PathVariable UUID cartId,
            @Parameter(description = "Refresh the local snapshot from the remote MCP cart before returning it.")
            @RequestParam(defaultValue = "false") boolean refresh
    ) {
        return CartResponse.from(cartService.get(new GetCartQuery(cartId, refresh)));
    }

    @PatchMapping("/{cartId}")
    @Operation(
            summary = "Update cart",
            description = "Adds items, updates line quantities, removes lines, and stores the refreshed remote cart snapshot."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Updated cart snapshot",
            content = @Content(schema = @Schema(implementation = CartResponse.class))
    )
    public CartResponse update(
            @Parameter(description = "Local cart UUID.", required = true)
            @PathVariable UUID cartId,
            @Valid @RequestBody CartUpdateRequest request
    ) {
        return CartResponse.from(
                cartService.update(CartCommandMapper.toCommand(cartId, request))
        );
    }

    @GetMapping("/{cartId}/checkout")
    @Operation(
            summary = "Get cart checkout URL",
            description = "Returns the stored checkout URL, or refreshes the cart from MCP when missing or requested."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Cart checkout URL",
            content = @Content(schema = @Schema(implementation = CheckoutResponse.class))
    )
    public CheckoutResponse checkout(
            @Parameter(description = "Local cart UUID.", required = true)
            @PathVariable UUID cartId,
            @Parameter(description = "Refresh the local snapshot from the remote MCP cart before returning checkout.")
            @RequestParam(defaultValue = "false") boolean refresh
    ) {
        return CheckoutResponse.from(
                cartService.checkout(new GetCheckoutQuery(cartId, refresh))
        );
    }

}
