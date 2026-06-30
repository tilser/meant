package com.meant.api.module.cart.controller;

import com.meant.api.module.cart.controller.mapper.CartCommandMapper;
import com.meant.api.module.cart.controller.request.CancelCheckoutRequest;
import com.meant.api.module.cart.controller.request.CartCreateRequest;
import com.meant.api.module.cart.controller.request.CartUpdateRequest;
import com.meant.api.module.cart.controller.request.CompleteCheckoutRequest;
import com.meant.api.module.cart.controller.response.CheckoutCompletionResponse;
import com.meant.api.module.cart.controller.response.CartResponse;
import com.meant.api.module.cart.controller.response.CheckoutResponse;
import com.meant.api.module.cart.service.CartService;
import com.meant.api.module.cart.service.command.CancelCartCommand;
import com.meant.api.module.cart.service.query.GetCartQuery;
import com.meant.api.module.cart.service.query.GetCheckoutQuery;
import com.meant.api.module.user.service.UserService;
import com.meant.api.module.user.service.command.UpsertUserCommand;
import com.meant.api.module.user.service.dto.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/carts")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Carts", description = "Testing endpoints for MCP-backed carts")
public class CartController {

    private final CartService cartService;
    private final UserService userService;

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
    public CartResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CartCreateRequest request
    ) {
        AuthenticatedUser authenticatedUser = authenticatedUser(jwt);
        userService.upsert(toUpsertCommand(authenticatedUser));
        return CartResponse.from(cartService.create(CartCommandMapper.toCommand(authenticatedUser.id(), request)));
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
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Local cart UUID.", required = true)
            @PathVariable UUID cartId,
            @Parameter(description = "Refresh the local snapshot from the remote MCP cart before returning it.")
            @RequestParam(defaultValue = "false") boolean refresh
    ) {
        AuthenticatedUser authenticatedUser = authenticatedUser(jwt);
        return CartResponse.from(cartService.get(new GetCartQuery(cartId, authenticatedUser.id(), refresh)));
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
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Local cart UUID.", required = true)
            @PathVariable UUID cartId,
            @Valid @RequestBody CartUpdateRequest request
    ) {
        AuthenticatedUser authenticatedUser = authenticatedUser(jwt);
        return CartResponse.from(
                cartService.update(CartCommandMapper.toCommand(cartId, authenticatedUser.id(), request))
        );
    }

    @DeleteMapping("/{cartId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Cancel cart",
            description = "Cancels the remote UCP cart and deactivates the local cart snapshot."
    )
    @ApiResponse(responseCode = "204", description = "Cart canceled")
    public void cancel(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Local cart UUID.", required = true)
            @PathVariable UUID cartId
    ) {
        AuthenticatedUser authenticatedUser = authenticatedUser(jwt);
        cartService.cancel(new CancelCartCommand(cartId, authenticatedUser.id()));
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
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Local cart UUID.", required = true)
            @PathVariable UUID cartId,
            @Parameter(description = "Refresh the local snapshot from the remote MCP cart before returning checkout.")
            @RequestParam(defaultValue = "false") boolean refresh
    ) {
        AuthenticatedUser authenticatedUser = authenticatedUser(jwt);
        return CheckoutResponse.from(
                cartService.checkout(new GetCheckoutQuery(cartId, authenticatedUser.id(), refresh))
        );
    }

    @PostMapping("/{cartId}/checkout/complete")
    @Operation(
            summary = "Complete cart checkout natively",
            description = "Completes checkout through the merchant UCP complete_checkout tool when native checkout is enabled for the merchant."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Native checkout completion result",
            content = @Content(schema = @Schema(implementation = CheckoutCompletionResponse.class))
    )
    public CheckoutCompletionResponse completeCheckout(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Local cart UUID.", required = true)
            @PathVariable UUID cartId,
            @Valid @RequestBody CompleteCheckoutRequest request
    ) {
        AuthenticatedUser authenticatedUser = authenticatedUser(jwt);
        return CheckoutCompletionResponse.from(
                cartService.completeCheckout(CartCommandMapper.toCommand(cartId, authenticatedUser.id(), request))
        );
    }

    @PostMapping("/{cartId}/checkout/cancel")
    @Operation(
            summary = "Cancel native cart checkout",
            description = "Cancels checkout through the merchant UCP cancel_checkout tool unless completion is already in flight."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Native checkout cancellation result",
            content = @Content(schema = @Schema(implementation = CheckoutCompletionResponse.class))
    )
    public CheckoutCompletionResponse cancelCheckout(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Local cart UUID.", required = true)
            @PathVariable UUID cartId,
            @Valid @RequestBody CancelCheckoutRequest request
    ) {
        AuthenticatedUser authenticatedUser = authenticatedUser(jwt);
        return CheckoutCompletionResponse.from(
                cartService.cancelCheckout(CartCommandMapper.toCommand(cartId, authenticatedUser.id(), request))
        );
    }

    private AuthenticatedUser authenticatedUser(Jwt jwt) {
        return AuthenticatedUser.fromJwt(jwt);
    }

    private UpsertUserCommand toUpsertCommand(AuthenticatedUser authenticatedUser) {
        return new UpsertUserCommand(
                authenticatedUser.id(),
                authenticatedUser.email(),
                authenticatedUser.firstName(),
                authenticatedUser.surname()
        );
    }
}
