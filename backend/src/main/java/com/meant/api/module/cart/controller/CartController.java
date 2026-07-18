package com.meant.api.module.cart.controller;

import com.meant.api.module.cart.controller.mapper.CartCommandMapper;
import com.meant.api.module.cart.controller.request.AssistCheckoutRequest;
import com.meant.api.module.cart.controller.request.CancelCheckoutRequest;
import com.meant.api.module.cart.controller.request.CartCreateRequest;
import com.meant.api.module.cart.controller.request.CartUpdateRequest;
import com.meant.api.module.cart.controller.request.CheckoutUpdateRequest;
import com.meant.api.module.cart.controller.request.CompleteCheckoutRequest;
import com.meant.api.module.cart.controller.request.CreateCheckoutConsentRequest;
import com.meant.api.module.cart.controller.response.CheckoutAssistResponse;
import com.meant.api.module.cart.controller.response.CheckoutConsentResponse;
import com.meant.api.module.cart.controller.response.CheckoutCompletionResponse;
import com.meant.api.module.cart.controller.response.CartResponse;
import com.meant.api.module.cart.controller.response.CheckoutResponse;
import com.meant.api.module.cart.controller.response.EmbeddedCheckoutBootstrapResponse;
import com.meant.api.module.cart.service.CartService;
import com.meant.api.module.cart.service.EmbeddedCheckoutBootstrapService;
import com.meant.api.module.cart.service.CheckoutAssistantService;
import com.meant.api.module.cart.service.command.AssistCheckoutCommand;
import com.meant.api.module.cart.service.command.CancelCartCommand;
import com.meant.api.module.cart.service.query.GetCartQuery;
import com.meant.api.module.cart.service.query.GetCheckoutQuery;
import com.meant.api.module.user.service.UserService;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.dto.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
    private final CheckoutAssistantService checkoutAssistantService;
    private final EmbeddedCheckoutBootstrapService embeddedCheckoutBootstrapService;
    private final UserService userService;

    @PostMapping
    @Operation(
            summary = "Create cart",
            description = "Resolves server-issued offer keys from the authenticated user's live catalog session "
                    + "or durable saved-product selection, revalidates exact commercial identity, and creates one "
                    + "merchant-scoped remote cart."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Created cart snapshot",
            content = @Content(schema = @Schema(implementation = CartResponse.class))
    )
    public CartResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CartCreateRequest request,
            HttpServletRequest httpRequest
    ) {
        AuthenticatedUser authenticatedUser = authenticatedUser(jwt);
        userService.ensureProfile(toEnsureProfileCommand(authenticatedUser));
        return CartResponse.from(cartService.create(CartCommandMapper.toCommand(
                authenticatedUser.id(), request, httpRequest.getRemoteAddr())));
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
            @RequestParam(defaultValue = "false") boolean refresh,
            HttpServletRequest httpRequest
    ) {
        AuthenticatedUser authenticatedUser = authenticatedUser(jwt);
        return CartResponse.from(cartService.get(new GetCartQuery(
                cartId, authenticatedUser.id(), refresh, httpRequest.getRemoteAddr())));
    }

    @PatchMapping("/{cartId}")
    @Operation(
            summary = "Update cart",
            description = "Adds server-resolved offers only when they match the cart's immutable provider and merchant scope, "
                    + "updates quantities, and removes lines."
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
            @Valid @RequestBody CartUpdateRequest request,
            HttpServletRequest httpRequest
    ) {
        AuthenticatedUser authenticatedUser = authenticatedUser(jwt);
        return CartResponse.from(
                cartService.update(CartCommandMapper.toCommand(
                        cartId, authenticatedUser.id(), request, httpRequest.getRemoteAddr()))
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
            @PathVariable UUID cartId,
            HttpServletRequest httpRequest
    ) {
        AuthenticatedUser authenticatedUser = authenticatedUser(jwt);
        cartService.cancel(new CancelCartCommand(cartId, authenticatedUser.id(), httpRequest.getRemoteAddr()));
    }

    @GetMapping("/{cartId}/checkout")
    @Operation(
            summary = "Get cart checkout session",
            description = "Creates or refreshes a UCP checkout session for in-page checkout. continueUrl is returned for iframe escalation only."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Cart checkout session",
            content = @Content(schema = @Schema(implementation = CheckoutResponse.class))
    )
    public CheckoutResponse checkout(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Local cart UUID.", required = true)
            @PathVariable UUID cartId,
            @Parameter(description = "Refresh the checkout session from the remote UCP checkout when possible.")
            @RequestParam(defaultValue = "false") boolean refresh,
            HttpServletRequest httpRequest
    ) {
        AuthenticatedUser authenticatedUser = authenticatedUser(jwt);
        return CheckoutResponse.from(
                cartService.checkout(new GetCheckoutQuery(
                        cartId, authenticatedUser.id(), refresh, httpRequest.getRemoteAddr()))
        );
    }

    @PostMapping("/{cartId}/checkout/embedded")
    @Operation(summary = "Create an embedded checkout bootstrap session")
    @ApiResponse(responseCode = "200", description = "Embedded checkout or safe fallback instructions",
            content = @Content(schema = @Schema(implementation = EmbeddedCheckoutBootstrapResponse.class)))
    public ResponseEntity<EmbeddedCheckoutBootstrapResponse> bootstrapEmbeddedCheckout(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID cartId,
            @RequestHeader("Origin") String origin,
            HttpServletRequest httpRequest
    ) {
        AuthenticatedUser authenticatedUser = authenticatedUser(jwt);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(EmbeddedCheckoutBootstrapResponse.from(
                        embeddedCheckoutBootstrapService.bootstrap(
                                cartId, authenticatedUser.id(), origin, httpRequest.getRemoteAddr())));
    }

    @PostMapping("/{cartId}/checkout/embedded/{sessionId}/opened")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    @Operation(summary = "Acknowledge a confirmed embedded checkout start")
    @ApiResponse(responseCode = "204", description = "Embedded checkout start acknowledged idempotently")
    public void acknowledgeEmbeddedCheckoutOpened(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID cartId,
            @PathVariable UUID sessionId,
            @RequestHeader("Origin") String origin
    ) {
        AuthenticatedUser authenticatedUser = authenticatedUser(jwt);
        embeddedCheckoutBootstrapService.opened(cartId, sessionId, authenticatedUser.id(), origin);
    }

    @PostMapping("/{cartId}/checkout/embedded/{sessionId}/complete")
    @Operation(summary = "Verify an embedded checkout completion")
    @ApiResponse(responseCode = "200", description = "Provider-verified completed checkout",
            content = @Content(schema = @Schema(implementation = CheckoutResponse.class)))
    public ResponseEntity<CheckoutResponse> completeEmbeddedCheckout(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID cartId,
            @PathVariable UUID sessionId,
            @RequestHeader("Origin") String origin,
            HttpServletRequest httpRequest
    ) {
        AuthenticatedUser authenticatedUser = authenticatedUser(jwt);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CheckoutResponse.from(
                        embeddedCheckoutBootstrapService.complete(
                                cartId, sessionId, authenticatedUser.id(), origin, httpRequest.getRemoteAddr())));
    }

    @PostMapping("/{cartId}/checkout/embedded/{sessionId}/cancel")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    @Operation(summary = "Close an embedded checkout host session without cancelling the remote checkout")
    @ApiResponse(responseCode = "204", description = "Embedded host session closed")
    public void cancelEmbeddedCheckout(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID cartId,
            @PathVariable UUID sessionId,
            @RequestHeader("Origin") String origin
    ) {
        AuthenticatedUser authenticatedUser = authenticatedUser(jwt);
        embeddedCheckoutBootstrapService.cancel(cartId, sessionId, authenticatedUser.id(), origin);
    }

    @PatchMapping("/{cartId}/checkout")
    @Operation(
            summary = "Update cart checkout session",
            description = "Updates buyer and fulfillment details on the active UCP checkout session before native completion."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Updated cart checkout session",
            content = @Content(schema = @Schema(implementation = CheckoutResponse.class))
    )
    public CheckoutResponse updateCheckout(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Local cart UUID.", required = true)
            @PathVariable UUID cartId,
            @Valid @RequestBody CheckoutUpdateRequest request,
            HttpServletRequest httpRequest
    ) {
        AuthenticatedUser authenticatedUser = authenticatedUser(jwt);
        return CheckoutResponse.from(
                cartService.updateCheckout(CartCommandMapper.toCommand(
                        cartId, authenticatedUser.id(), request, httpRequest.getRemoteAddr()))
        );
    }

    @PostMapping("/{cartId}/checkout/assistant")
    @Operation(
            summary = "Chat with the checkout assistant",
            description = "Conversational helper that asks the buyer for the pieces the merchant still needs "
                    + "(buyer identity, shipping destination) and applies them to the active UCP checkout session."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Assistant reply with the current checkout session",
            content = @Content(schema = @Schema(implementation = CheckoutAssistResponse.class))
    )
    public CheckoutAssistResponse assistCheckout(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Local cart UUID.", required = true)
            @PathVariable UUID cartId,
            @Valid @RequestBody AssistCheckoutRequest request,
            HttpServletRequest httpRequest
    ) {
        AuthenticatedUser authenticatedUser = authenticatedUser(jwt);
        return CheckoutAssistResponse.from(checkoutAssistantService.assist(new AssistCheckoutCommand(
                cartId,
                authenticatedUser.id(),
                request.message(),
                request.merchantDeliveryHint(),
                request.history() == null
                        ? List.of()
                        : request.history().stream()
                                .map(message -> new AssistCheckoutCommand.HistoryMessage(
                                        message.role(),
                                        message.content()
                                ))
                                .toList(),
                httpRequest.getRemoteAddr()
        )));
    }

    @PostMapping("/{cartId}/checkout/consent")
    @Operation(
            summary = "Record checkout consent",
            description = "Records buyer consent for the active UCP checkout session before native completion."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Checkout consent artifact",
            content = @Content(schema = @Schema(implementation = CheckoutConsentResponse.class))
    )
    public CheckoutConsentResponse recordCheckoutConsent(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Local cart UUID.", required = true)
            @PathVariable UUID cartId,
            @Valid @RequestBody CreateCheckoutConsentRequest request
    ) {
        AuthenticatedUser authenticatedUser = authenticatedUser(jwt);
        return CheckoutConsentResponse.from(
                cartService.recordCheckoutConsent(CartCommandMapper.toCommand(cartId, authenticatedUser.id(), request))
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
            @Valid @RequestBody CompleteCheckoutRequest request,
            HttpServletRequest httpRequest
    ) {
        AuthenticatedUser authenticatedUser = authenticatedUser(jwt);
        return CheckoutCompletionResponse.from(
                cartService.completeCheckout(CartCommandMapper.toCommand(
                        cartId, authenticatedUser.id(), request, httpRequest.getRemoteAddr()))
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
            @Valid @RequestBody CancelCheckoutRequest request,
            HttpServletRequest httpRequest
    ) {
        AuthenticatedUser authenticatedUser = authenticatedUser(jwt);
        return CheckoutCompletionResponse.from(
                cartService.cancelCheckout(CartCommandMapper.toCommand(
                        cartId, authenticatedUser.id(), request, httpRequest.getRemoteAddr()))
        );
    }

    private AuthenticatedUser authenticatedUser(Jwt jwt) {
        return AuthenticatedUser.fromJwt(jwt);
    }

    private EnsureUserProfileCommand toEnsureProfileCommand(AuthenticatedUser authenticatedUser) {
        return new EnsureUserProfileCommand(
                authenticatedUser.id(),
                authenticatedUser.email(),
                authenticatedUser.firstName(),
                authenticatedUser.surname()
        );
    }
}
