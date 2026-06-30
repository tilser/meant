package com.meant.api.module.order.controller;

import com.meant.api.module.order.controller.response.OrderResponse;
import com.meant.api.module.order.service.OrderService;
import com.meant.api.module.order.service.query.GetOrderQuery;
import com.meant.api.module.order.service.query.ListOrdersQuery;
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
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Orders", description = "Merchant-backed user orders")
public class OrderController {

    private final OrderService orderService;
    private final UserService userService;

    @GetMapping
    @Operation(summary = "List orders", description = "Returns stored merchant order state for the authenticated user.")
    @ApiResponse(
            responseCode = "200",
            description = "Stored merchant orders",
            content = @Content(schema = @Schema(implementation = OrderResponse.class))
    )
    public List<OrderResponse> list(@AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser authenticatedUser = authenticatedUser(jwt);
        userService.upsert(toUpsertCommand(authenticatedUser));
        return orderService.list(new ListOrdersQuery(authenticatedUser.id())).stream()
                .map(OrderResponse::from)
                .toList();
    }

    @GetMapping("/{orderId}")
    @Operation(
            summary = "Get order",
            description = "Returns stored merchant order state, refreshing from UCP get_order only when requested."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Stored merchant order",
            content = @Content(schema = @Schema(implementation = OrderResponse.class))
    )
    public OrderResponse get(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Local order UUID.", required = true)
            @PathVariable UUID orderId,
            @Parameter(description = "Refresh the local snapshot from the merchant UCP get_order tool before returning.")
            @RequestParam(defaultValue = "false") boolean refresh
    ) {
        AuthenticatedUser authenticatedUser = authenticatedUser(jwt);
        return OrderResponse.from(orderService.get(new GetOrderQuery(orderId, authenticatedUser.id(), refresh)));
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
