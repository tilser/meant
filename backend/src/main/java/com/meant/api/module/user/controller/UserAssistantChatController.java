package com.meant.api.module.user.controller;

import com.meant.api.module.user.controller.mapper.UserCommandMapper;
import com.meant.api.module.user.controller.request.UserAssistantChatContextRequest;
import com.meant.api.module.user.controller.request.UserAssistantChatRequest;
import com.meant.api.module.user.controller.response.UserAssistantConversationResponse;
import com.meant.api.module.user.controller.response.UserAssistantConversationSummaryResponse;
import com.meant.api.module.user.controller.response.UserAssistantStreamEventResponse;
import com.meant.api.module.user.service.UserAssistantChatService;
import com.meant.api.module.user.service.command.SendUserAssistantMessageCommand;
import com.meant.api.module.user.service.dto.AuthenticatedUser;
import com.meant.api.module.user.service.dto.UserAssistantPageContext;
import com.meant.api.module.user.service.query.GetLatestUserAssistantConversationQuery;
import com.meant.api.module.user.service.query.GetUserAssistantConversationQuery;
import com.meant.api.module.user.service.query.ListUserAssistantConversationsQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Users", description = "Profile endpoints for the authenticated Supabase user")
public class UserAssistantChatController {

    /**
     * Upper bound for the client-controlled assistant-conversation page size. Mirrors the
     * {@code @Max} on {@link ListUserAssistantConversationsQuery}; the controller clamps to it so
     * oversized requests return bounded results instead of being rejected (OWASP API4 -
     * Unrestricted Resource Consumption).
     */
    private static final int MAX_CONVERSATION_LIMIT = 50;

    private final UserAssistantChatService userAssistantChatService;
    private final UserStreamEventWriter userStreamEventWriter;

    @GetMapping("/me/assistant/conversations")
    @Operation(
            summary = "List Ask Meant conversations",
            description = "Returns recent persisted Ask Meant conversations for the authenticated user."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Recent Ask Meant conversations",
            content = @Content(schema = @Schema(implementation = UserAssistantConversationSummaryResponse.class))
    )
    public List<UserAssistantConversationSummaryResponse> assistantConversations(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "20") int limit
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        return userAssistantChatService.list(
                        UserCommandMapper.toEnsureProfileCommand(authenticatedUser),
                        new ListUserAssistantConversationsQuery(authenticatedUser.id(), clampConversationLimit(limit)))
                .stream()
                .map(UserAssistantConversationSummaryResponse::from)
                .toList();
    }

    @GetMapping("/me/assistant/conversations/latest")
    @Operation(
            summary = "Get latest Ask Meant conversation",
            description = "Returns the latest persisted floating Ask Meant conversation for the authenticated user."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Latest Ask Meant conversation",
            content = @Content(schema = @Schema(implementation = UserAssistantConversationResponse.class))
    )
    public UserAssistantConversationResponse latestAssistantConversation(@AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        return UserAssistantConversationResponse.from(userAssistantChatService.latest(
                UserCommandMapper.toEnsureProfileCommand(authenticatedUser),
                new GetLatestUserAssistantConversationQuery(authenticatedUser.id())));
    }

    @GetMapping("/me/assistant/conversations/{conversationId}")
    @Operation(
            summary = "Get an Ask Meant conversation",
            description = "Returns one persisted Ask Meant conversation and its messages for the authenticated user."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Ask Meant conversation",
            content = @Content(schema = @Schema(implementation = UserAssistantConversationResponse.class))
    )
    public UserAssistantConversationResponse assistantConversation(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID conversationId
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        return UserAssistantConversationResponse.from(userAssistantChatService.get(
                UserCommandMapper.toEnsureProfileCommand(authenticatedUser),
                new GetUserAssistantConversationQuery(authenticatedUser.id(), conversationId)));
    }

    @PostMapping(value = "/me/assistant/messages:stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "Stream an Ask Meant response",
            description = "Persists the user's floating Ask Meant message, streams the assistant answer, and stores "
                    + "the completed assistant message."
    )
    public StreamingResponseBody streamAssistantMessage(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UserAssistantChatRequest request
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        SendUserAssistantMessageCommand command = new SendUserAssistantMessageCommand(
                authenticatedUser.id(),
                request.conversationId(),
                request.message(),
                toPageContext(request.context())
        );
        return outputStream -> {
            try {
                userAssistantChatService.stream(
                        UserCommandMapper.toEnsureProfileCommand(authenticatedUser),
                        command,
                        event -> userStreamEventWriter.writeAssistantEvent(
                                outputStream,
                                UserAssistantStreamEventResponse.from(event))
                );
            } catch (UncheckedIOException exception) {
                throw exception.getCause();
            } catch (RuntimeException exception) {
                userStreamEventWriter.writeAssistantEvent(outputStream, UserAssistantStreamEventResponse.error(
                        "Ask Meant could not respond right now. Try again in a moment."
                ));
            }
        };
    }

    private int clampConversationLimit(int limit) {
        return Math.max(1, Math.min(limit, MAX_CONVERSATION_LIMIT));
    }

    private UserAssistantPageContext toPageContext(UserAssistantChatContextRequest request) {
        if (request == null) {
            return null;
        }
        return new UserAssistantPageContext(
                request.view(),
                request.contextLabel(),
                request.currentSearchQuery(),
                request.selectedMerchantName(),
                request.savedProductCount(),
                request.cartItemCount(),
                request.visibleProducts() == null ? List.of() : request.visibleProducts().stream()
                        .filter(Objects::nonNull)
                        .map(product -> new UserAssistantPageContext.Product(
                                product.id(),
                                product.name(),
                                product.brand(),
                                product.category(),
                                product.match(),
                                product.priceFrom(),
                                product.note()))
                        .toList(),
                request.cartItems() == null ? List.of() : request.cartItems().stream()
                        .filter(Objects::nonNull)
                        .map(item -> new UserAssistantPageContext.CartItem(
                                item.name(),
                                item.merchant(),
                                item.quantity(),
                                item.price()))
                        .toList(),
                request.orders() == null ? List.of() : request.orders().stream()
                        .filter(Objects::nonNull)
                        .map(order -> new UserAssistantPageContext.Order(
                                order.id(),
                                order.date(),
                                order.status(),
                                order.statusNote(),
                                order.itemCount()))
                        .toList()
        );
    }
}
