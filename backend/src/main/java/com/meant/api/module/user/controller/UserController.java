package com.meant.api.module.user.controller;

import com.meant.api.module.user.constant.UserInventoryCategory;
import com.meant.api.module.user.constant.UserProductDiscoverySortDirection;
import com.meant.api.module.user.constant.UserProductDiscoverySortField;
import com.meant.api.module.user.controller.request.AddUserInventoryItemRequest;
import com.meant.api.module.user.controller.request.AddUserInventoryPhotoRequest;
import com.meant.api.module.user.controller.mapper.UserCommandMapper;
import com.meant.api.module.user.controller.request.RecordUserTasteBehaviorRequest;
import com.meant.api.module.user.controller.request.SaveUserProductRequest;
import com.meant.api.module.user.controller.request.UpdateUserInventoryItemRequest;
import com.meant.api.module.user.controller.request.UpdateUserProfilePictureRequest;
import com.meant.api.module.user.controller.request.UpdateUserProfileRequest;
import com.meant.api.module.user.controller.request.UpdateUserSettingsRequest;
import com.meant.api.module.user.controller.request.UpdateUserTasteSignalRequest;
import com.meant.api.module.user.controller.request.UserAssistantChatContextRequest;
import com.meant.api.module.user.controller.request.UserAssistantChatRequest;
import com.meant.api.module.user.controller.request.UserProductSearchRequest;
import com.meant.api.module.user.controller.response.UserAssistantConversationResponse;
import com.meant.api.module.user.controller.response.UserAssistantConversationSummaryResponse;
import com.meant.api.module.user.controller.response.UserAssistantStreamEventResponse;
import com.meant.api.module.user.controller.response.UserInventoryExportResponse;
import com.meant.api.module.user.controller.response.UserInventoryItemResponse;
import com.meant.api.module.user.controller.response.UserPopularProductSearchResponse;
import com.meant.api.module.user.controller.response.UserProductDiscoveryResponse;
import com.meant.api.module.user.controller.response.UserProductSearchResponse;
import com.meant.api.module.user.controller.response.UserProductSearchSuggestionsResponse;
import com.meant.api.module.user.controller.response.UserProductSearchStreamEventResponse;
import com.meant.api.module.user.controller.response.UserResponse;
import com.meant.api.module.user.controller.response.UserSavedProductResponse;
import com.meant.api.module.user.controller.response.UserSettingsResponse;
import com.meant.api.module.user.controller.response.UserTasteProfileResponse;
import com.meant.api.module.user.controller.response.UserTasteSignalResponse;
import com.meant.api.module.user.properties.UserCollectionProperties;
import com.meant.api.module.user.service.UserAssistantChatService;
import com.meant.api.module.user.service.UserInventoryService;
import com.meant.api.module.user.service.UserPreferenceFilterParsingService;
import com.meant.api.module.user.service.UserProductDiscoveryService;
import com.meant.api.module.user.service.UserProductSearchEventService;
import com.meant.api.module.user.service.UserProductSearchService;
import com.meant.api.module.user.service.UserProductSearchSuggestionService;
import com.meant.api.module.user.service.UserSavedProductService;
import com.meant.api.module.user.service.UserService;
import com.meant.api.module.user.service.UserSettingsService;
import com.meant.api.module.user.service.UserTasteProfileService;
import com.meant.api.module.user.service.dto.AuthenticatedUser;
import com.meant.api.module.user.service.command.AcceptUserTasteSuggestionCommand;
import com.meant.api.module.user.service.command.DeleteUserInventoryItemCommand;
import com.meant.api.module.user.service.command.ParseUserPreferenceFiltersCommand;
import com.meant.api.module.user.service.command.RemoveSavedProductCommand;
import com.meant.api.module.user.service.command.SendUserAssistantMessageCommand;
import com.meant.api.module.user.service.command.SearchUserProductsCommand;
import com.meant.api.module.user.service.dto.ParsedUserPreferenceFilters;
import com.meant.api.module.user.service.dto.UserAssistantPageContext;
import com.meant.api.module.user.service.query.ExportUserInventoryQuery;
import com.meant.api.module.user.service.query.GetLatestUserAssistantConversationQuery;
import com.meant.api.module.user.service.query.GetUserAssistantConversationQuery;
import com.meant.api.module.user.service.query.GetUserProductDiscoveryQuery;
import com.meant.api.module.user.service.query.GetUserTasteProfileQuery;
import com.meant.api.module.user.service.query.ListUserInventoryItemsQuery;
import com.meant.api.module.user.service.query.ListSavedProductsQuery;
import com.meant.api.module.user.service.query.ListUserAssistantConversationsQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Users", description = "Profile endpoints for the authenticated Supabase user")
public class UserController {

    /**
     * Upper bound for the client-controlled assistant-conversation page size. Mirrors the
     * {@code @Max} on {@link ListUserAssistantConversationsQuery}; the controller clamps to it so
     * oversized requests return bounded results instead of being rejected (OWASP API4 —
     * Unrestricted Resource Consumption).
     */
    private static final int MAX_CONVERSATION_LIMIT = 50;

    private final UserService userService;
    private final UserSettingsService userSettingsService;
    private final UserPreferenceFilterParsingService userPreferenceFilterParsingService;
    private final UserAssistantChatService userAssistantChatService;
    private final UserProductDiscoveryService userProductDiscoveryService;
    private final UserProductSearchEventService userProductSearchEventService;
    private final UserProductSearchService userProductSearchService;
    private final UserProductSearchSuggestionService userProductSearchSuggestionService;
    private final UserSavedProductService userSavedProductService;
    private final UserInventoryService userInventoryService;
    private final UserTasteProfileService userTasteProfileService;
    private final UserCollectionProperties userCollectionProperties;
    private final ObjectMapper objectMapper;

    @GetMapping("/me")
    @Operation(
            summary = "Get current user",
            description = "Returns the profile of the authenticated user, creating it from the Supabase "
                    + "JWT on first call (upsert-on-read)."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Current user profile",
            content = @Content(schema = @Schema(implementation = UserResponse.class))
    )
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        return UserResponse.from(userService.upsert(UserCommandMapper.toUpsertCommand(authenticatedUser)));
    }

    @PatchMapping("/me")
    @Operation(
            summary = "Update current user profile",
            description = "Updates the first name and surname of the authenticated user."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Updated user profile",
            content = @Content(schema = @Schema(implementation = UserResponse.class))
    )
    public UserResponse updateMe(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateUserProfileRequest request
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        // Upsert-from-JWT and the name edit run in a single service transaction (the row may not exist
        // yet if a client PATCHes before ever calling GET /me).
        return UserResponse.from(userService.updateProfile(
                UserCommandMapper.toUpsertCommand(authenticatedUser),
                UserCommandMapper.toUpdateCommand(authenticatedUser.id(), request)));
    }

    @PatchMapping("/me/profile-picture")
    @Operation(
            summary = "Update current user profile picture",
            description = "Stores the Supabase Storage object path for the authenticated user's profile picture."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Updated user profile",
            content = @Content(schema = @Schema(implementation = UserResponse.class))
    )
    public UserResponse updateProfilePicture(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateUserProfilePictureRequest request
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        return UserResponse.from(userService.updateProfilePicture(
                UserCommandMapper.toUpsertCommand(authenticatedUser),
                UserCommandMapper.toUpdateCommand(authenticatedUser.id(), request)));
    }

    @DeleteMapping("/me/profile-picture")
    @Operation(
            summary = "Remove current user profile picture",
            description = "Clears the stored profile picture object path for the authenticated user."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Updated user profile",
            content = @Content(schema = @Schema(implementation = UserResponse.class))
    )
    public UserResponse removeProfilePicture(@AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        return UserResponse.from(userService.removeProfilePicture(
                UserCommandMapper.toUpsertCommand(authenticatedUser)));
    }

    @GetMapping("/me/settings")
    @Operation(
            summary = "Get current user settings",
            description = "Returns the current user's shopping settings and canonical filter catalog."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Current user settings",
            content = @Content(schema = @Schema(implementation = UserSettingsResponse.class))
    )
    public UserSettingsResponse settings(@AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        return UserSettingsResponse.from(userSettingsService.get(
                UserCommandMapper.toUpsertCommand(authenticatedUser)));
    }

    @PatchMapping("/me/settings")
    @Operation(
            summary = "Update current user settings",
            description = "Updates shopping settings. When preferenceDescription is present, it is parsed into "
                    + "canonical shopping filters and merged into the active filter set before saving."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Updated user settings",
            content = @Content(schema = @Schema(implementation = UserSettingsResponse.class))
    )
    public UserSettingsResponse updateSettings(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateUserSettingsRequest request
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        ParsedUserPreferenceFilters parsedFilters = parseFilters(authenticatedUser, request);
        return UserSettingsResponse.from(userSettingsService.update(
                UserCommandMapper.toUpsertCommand(authenticatedUser),
                UserCommandMapper.toUpdateSettingsCommand(authenticatedUser.id(), request, parsedFilters)));
    }

    @GetMapping("/me/taste-profile")
    @Operation(
            summary = "Get learned taste profile",
            description = "Returns behavioral taste signals learned from saves, purchases, dismissals, and repeat searches."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Learned taste profile for the current user",
            content = @Content(schema = @Schema(implementation = UserTasteProfileResponse.class))
    )
    public UserTasteProfileResponse tasteProfile(@AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        return UserTasteProfileResponse.from(userTasteProfileService.get(
                UserCommandMapper.toUpsertCommand(authenticatedUser),
                new GetUserTasteProfileQuery(authenticatedUser.id())));
    }

    @PostMapping("/me/taste-profile/behaviors")
    @Operation(
            summary = "Record product taste behavior",
            description = "Records a purchase or dismissal product snapshot so it can influence future ranking."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Updated learned taste profile",
            content = @Content(schema = @Schema(implementation = UserTasteProfileResponse.class))
    )
    public UserTasteProfileResponse recordTasteBehavior(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RecordUserTasteBehaviorRequest request
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        return UserTasteProfileResponse.from(userTasteProfileService.recordBehavior(
                UserCommandMapper.toUpsertCommand(authenticatedUser),
                UserCommandMapper.toRecordUserTasteBehaviorCommand(authenticatedUser.id(), request)));
    }

    @PatchMapping("/me/taste-profile/signals/{signalId}")
    @Operation(
            summary = "Edit a learned taste signal",
            description = "Updates the learned signal weight or disables/enables the signal for future ranking."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Updated learned taste signal",
            content = @Content(schema = @Schema(implementation = UserTasteSignalResponse.class))
    )
    public UserTasteSignalResponse updateTasteSignal(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID signalId,
            @Valid @RequestBody UpdateUserTasteSignalRequest request
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        return UserTasteSignalResponse.from(userTasteProfileService.updateSignal(
                UserCommandMapper.toUpsertCommand(authenticatedUser),
                UserCommandMapper.toUpdateUserTasteSignalCommand(authenticatedUser.id(), signalId, request)));
    }

    @DeleteMapping("/me/taste-profile/signals/{signalId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a learned taste signal")
    @ApiResponse(responseCode = "204", description = "Learned taste signal removed")
    public void removeTasteSignal(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID signalId
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        userTasteProfileService.removeSignal(
                UserCommandMapper.toUpsertCommand(authenticatedUser),
                authenticatedUser.id(),
                signalId);
    }

    @PostMapping("/me/taste-profile/suggestions/{filterId}:accept")
    @Operation(
            summary = "Accept a learned filter suggestion",
            description = "Adds the suggested explicit filter to current settings in one tap."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Updated user settings",
            content = @Content(schema = @Schema(implementation = UserSettingsResponse.class))
    )
    public UserSettingsResponse acceptTasteSuggestion(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String filterId
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        return UserSettingsResponse.from(userTasteProfileService.acceptSuggestion(
                UserCommandMapper.toUpsertCommand(authenticatedUser),
                new AcceptUserTasteSuggestionCommand(authenticatedUser.id(), filterId)));
    }

    @PostMapping("/me/taste-profile/suggestions/{filterId}:reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Reject a learned filter suggestion")
    @ApiResponse(responseCode = "204", description = "Learned filter suggestion rejected")
    public void rejectTasteSuggestion(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String filterId
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        userTasteProfileService.rejectSuggestion(
                UserCommandMapper.toUpsertCommand(authenticatedUser),
                new AcceptUserTasteSuggestionCommand(authenticatedUser.id(), filterId));
    }

    @PostMapping("/me/product-searches")
    @Operation(
            summary = "Search products for the current user",
            description = "Searches merchant catalogs for the query, explains why products fit the user's shopping "
                    + "profile, and caches product snapshots and explanations for repeated searches."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Product search results for the current user",
            content = @Content(schema = @Schema(implementation = UserProductSearchResponse.class))
    )
    public UserProductSearchResponse searchProducts(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UserProductSearchRequest request,
            HttpServletRequest httpRequest
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        return UserProductSearchResponse.from(userProductSearchService.search(
                UserCommandMapper.toUpsertCommand(authenticatedUser),
                new SearchUserProductsCommand(
                        authenticatedUser.id(),
                        request.query(),
                        request.merchantId(),
                        buyerIp(httpRequest),
                        userAgent(httpRequest),
                        request.offset(),
                        request.limit())));
    }

    @PostMapping(value = "/me/product-searches:stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "Stream product search results for the current user",
            description = "Streams product-search phases, catalog candidates, product enrichments, ranking updates, "
                    + "and final pagination metadata as soon as each piece is available."
    )
    public StreamingResponseBody streamSearchProducts(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UserProductSearchRequest request,
            HttpServletRequest httpRequest
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        SearchUserProductsCommand command = new SearchUserProductsCommand(
                authenticatedUser.id(),
                request.query(),
                request.merchantId(),
                buyerIp(httpRequest),
                userAgent(httpRequest),
                request.offset(),
                request.limit()
        );
        return outputStream -> {
            try {
                try {
                    userProductSearchService.stream(
                            UserCommandMapper.toUpsertCommand(authenticatedUser),
                            command,
                            event -> writeProductSearchEvent(outputStream, UserProductSearchStreamEventResponse.from(event))
                    );
                } catch (UncheckedIOException exception) {
                    throw exception;
                } catch (RuntimeException exception) {
                    log.warn(
                            "Product search stream failed. userId={}, merchantId={}",
                            authenticatedUser.id(),
                            command.merchantId(),
                            exception
                    );
                    writeProductSearchEvent(outputStream, UserProductSearchStreamEventResponse.error(
                            "Product search failed. Please try again."
                    ));
                }
            } catch (UncheckedIOException exception) {
                throw exception.getCause();
            }
        };
    }

    @GetMapping("/me/product-search-suggestions")
    @Operation(
            summary = "Generate product search suggestions",
            description = "Generates four fresh product search suggestions from the current user's active shopping "
                    + "filters."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Generated product search suggestions for the current user",
            content = @Content(schema = @Schema(implementation = UserProductSearchSuggestionsResponse.class))
    )
    public UserProductSearchSuggestionsResponse productSearchSuggestions(@AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        return UserProductSearchSuggestionsResponse.from(userProductSearchSuggestionService.generate(
                UserCommandMapper.toUpsertCommand(authenticatedUser)));
    }

    @GetMapping("/me/inventory")
    @Operation(
            summary = "List current user inventory",
            description = "Returns owned wardrobe, pantry, home, and other items for the authenticated user."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Inventory items for the current user",
            content = @Content(schema = @Schema(implementation = UserInventoryItemResponse.class))
    )
    public List<UserInventoryItemResponse> inventory(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UserInventoryCategory category,
            @RequestParam(defaultValue = "false") boolean restockOnly,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer limit
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        return userInventoryService.list(
                        UserCommandMapper.toUpsertCommand(authenticatedUser),
                        new ListUserInventoryItemsQuery(
                                authenticatedUser.id(),
                                category,
                                restockOnly,
                                pageValue(page),
                                limitValue(
                                        limit,
                                        userCollectionProperties.inventory().defaultLimit(),
                                        userCollectionProperties.inventory().maxLimit())))
                .stream()
                .map(UserInventoryItemResponse::from)
                .toList();
    }

    @GetMapping("/me/inventory/export")
    @Operation(
            summary = "Export current user inventory",
            description = "Returns an exportable copy of all owned inventory data for the authenticated user."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Exportable inventory payload",
            content = @Content(schema = @Schema(implementation = UserInventoryExportResponse.class))
    )
    public UserInventoryExportResponse exportInventory(@AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        return UserInventoryExportResponse.from(userInventoryService.export(
                UserCommandMapper.toUpsertCommand(authenticatedUser),
                new ExportUserInventoryQuery(authenticatedUser.id())));
    }

    @PostMapping("/me/inventory")
    @Operation(
            summary = "Add an owned inventory item",
            description = "Adds a manual wardrobe, pantry, home, or other owned item for the current user."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Created inventory item",
            content = @Content(schema = @Schema(implementation = UserInventoryItemResponse.class))
    )
    public UserInventoryItemResponse addInventoryItem(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AddUserInventoryItemRequest request
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        return UserInventoryItemResponse.from(userInventoryService.create(
                UserCommandMapper.toUpsertCommand(authenticatedUser),
                UserCommandMapper.toCreateInventoryItemCommand(authenticatedUser.id(), request)));
    }

    @PostMapping("/me/inventory/photos")
    @Operation(
            summary = "Add an inventory item from a photo",
            description = "Adds an owned item from a photo URL or data URL. When AI recognition is available, "
                    + "recognized fields are merged with user-provided fallback fields."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Created inventory item",
            content = @Content(schema = @Schema(implementation = UserInventoryItemResponse.class))
    )
    public UserInventoryItemResponse addInventoryPhoto(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AddUserInventoryPhotoRequest request
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        return UserInventoryItemResponse.from(userInventoryService.createFromPhoto(
                UserCommandMapper.toUpsertCommand(authenticatedUser),
                UserCommandMapper.toCreateInventoryPhotoItemCommand(authenticatedUser.id(), request)));
    }

    @PatchMapping("/me/inventory/{itemId}")
    @Operation(
            summary = "Update an owned inventory item",
            description = "Edits inventory item details, quantity, and restock settings for the current user."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Updated inventory item",
            content = @Content(schema = @Schema(implementation = UserInventoryItemResponse.class))
    )
    public UserInventoryItemResponse updateInventoryItem(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateUserInventoryItemRequest request
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        return UserInventoryItemResponse.from(userInventoryService.update(
                UserCommandMapper.toUpsertCommand(authenticatedUser),
                UserCommandMapper.toUpdateInventoryItemCommand(authenticatedUser.id(), itemId, request)));
    }

    @DeleteMapping("/me/inventory/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Delete an owned inventory item",
            description = "Deletes an inventory item owned by the current user."
    )
    @ApiResponse(responseCode = "204", description = "Inventory item deleted")
    public void deleteInventoryItem(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID itemId
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        userInventoryService.delete(
                UserCommandMapper.toUpsertCommand(authenticatedUser),
                new DeleteUserInventoryItemCommand(authenticatedUser.id(), itemId));
    }

    @GetMapping("/me/product-discovery")
    @Operation(
            summary = "Get product discovery context",
            description = "Returns product snapshots the authenticated user already owns through saved products "
                    + "and valid recent search caches. This endpoint does not search other users' data."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Product discovery context for the current user",
            content = @Content(schema = @Schema(implementation = UserProductDiscoveryResponse.class))
    )
    public UserProductDiscoveryResponse productDiscovery(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "recent") String sortBy,
            @RequestParam(required = false) String sortDirection
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        UserProductDiscoverySortField sortField = productDiscoverySortField(sortBy);
        return UserProductDiscoveryResponse.from(userProductDiscoveryService.get(
                UserCommandMapper.toUpsertCommand(authenticatedUser),
                new GetUserProductDiscoveryQuery(
                        authenticatedUser.id(),
                        blankToNull(search),
                        sortField,
                        productDiscoverySortDirection(sortDirection, sortField))));
    }

    @GetMapping("/me/popular-product-searches")
    @Operation(
            summary = "List popular product searches",
            description = "Returns anonymized popular product search prompts from recent aggregate search events."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Popular product search prompts",
            content = @Content(schema = @Schema(implementation = UserPopularProductSearchResponse.class))
    )
    public List<UserPopularProductSearchResponse> popularProductSearches(@AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser.fromJwt(jwt);
        return userProductSearchEventService.popular(Instant.now()).stream()
                .map(UserPopularProductSearchResponse::from)
                .toList();
    }

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
                        UserCommandMapper.toUpsertCommand(authenticatedUser),
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
                UserCommandMapper.toUpsertCommand(authenticatedUser),
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
                UserCommandMapper.toUpsertCommand(authenticatedUser),
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
                        UserCommandMapper.toUpsertCommand(authenticatedUser),
                        command,
                        event -> writeAssistantEvent(outputStream, UserAssistantStreamEventResponse.from(event))
                );
            } catch (UncheckedIOException exception) {
                throw exception.getCause();
            } catch (RuntimeException exception) {
                writeAssistantEvent(outputStream, UserAssistantStreamEventResponse.error(
                        "Ask Meant could not respond right now. Try again in a moment."
                ));
            }
        };
    }

    @GetMapping("/me/saved-products")
    @Operation(
            summary = "List saved products",
            description = "Returns the current user's saved product snapshots."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Saved products for the current user",
            content = @Content(schema = @Schema(implementation = UserSavedProductResponse.class))
    )
    public List<UserSavedProductResponse> savedProducts(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer limit
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        return userSavedProductService.list(
                        UserCommandMapper.toUpsertCommand(authenticatedUser),
                        new ListSavedProductsQuery(
                                authenticatedUser.id(),
                                pageValue(page),
                                limitValue(
                                        limit,
                                        userCollectionProperties.savedProducts().defaultLimit(),
                                        userCollectionProperties.savedProducts().maxLimit())))
                .stream()
                .map(UserSavedProductResponse::from)
                .toList();
    }

    @PostMapping("/me/saved-products")
    @Operation(
            summary = "Save a product",
            description = "Adds or refreshes a product snapshot in the current user's saved products."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Saved product snapshot",
            content = @Content(schema = @Schema(implementation = UserSavedProductResponse.class))
    )
    public UserSavedProductResponse saveProduct(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SaveUserProductRequest request
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        return UserSavedProductResponse.from(userSavedProductService.save(
                UserCommandMapper.toUpsertCommand(authenticatedUser),
                UserCommandMapper.toSaveUserProductCommand(authenticatedUser.id(), request)));
    }

    @DeleteMapping("/me/saved-products")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Remove a saved product",
            description = "Removes a product from the current user's saved products."
    )
    @ApiResponse(responseCode = "204", description = "Saved product removed")
    public void removeSavedProduct(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String productKey
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        userSavedProductService.remove(
                UserCommandMapper.toUpsertCommand(authenticatedUser),
                new RemoveSavedProductCommand(authenticatedUser.id(), productKey));
    }

    private ParsedUserPreferenceFilters parseFilters(
            AuthenticatedUser authenticatedUser,
            UpdateUserSettingsRequest request
    ) {
        if (request.preferenceDescription() == null || request.preferenceDescription().isBlank()) {
            return ParsedUserPreferenceFilters.empty();
        }
        return userPreferenceFilterParsingService.parse(new ParseUserPreferenceFiltersCommand(
                authenticatedUser.id(),
                request.preferenceDescription().trim()));
    }

    private int clampConversationLimit(int limit) {
        return Math.max(1, Math.min(limit, MAX_CONVERSATION_LIMIT));
    }

    private int pageValue(Integer page) {
        return page == null ? 0 : Math.max(0, page);
    }

    private int limitValue(Integer limit, int defaultLimit, int maxLimit) {
        if (limit == null) {
            return defaultLimit;
        }
        return Math.max(1, Math.min(limit, maxLimit));
    }

    private UserProductDiscoverySortField productDiscoverySortField(String value) {
        return enumValue(UserProductDiscoverySortField.class, value, "sortBy");
    }

    private UserProductDiscoverySortDirection productDiscoverySortDirection(
            String value,
            UserProductDiscoverySortField sortField
    ) {
        if (value == null || value.isBlank()) {
            return switch (sortField) {
                case NAME, PRICE -> UserProductDiscoverySortDirection.ASC;
                case MATCH, RECENT, RATING -> UserProductDiscoverySortDirection.DESC;
            };
        }
        return enumValue(UserProductDiscoverySortDirection.class, value, "sortDirection");
    }

    private <T extends Enum<T>> T enumValue(Class<T> type, String value, String parameterName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing " + parameterName);
        }
        try {
            return Enum.valueOf(type, value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid " + parameterName + ": " + value, exception);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * Resolves the client IP without parsing client-supplied forwarding headers in application code.
     * With the default {@code server.forward-headers-strategy=none}, {@code getRemoteAddr()} is the
     * real socket peer and attacker-set {@code X-Forwarded-For}/{@code Forwarded} headers are ignored
     * entirely. Behind a trusted reverse proxy, set the strategy to {@code native} so Tomcat's
     * {@code RemoteIpValve} rewrites {@code getRemoteAddr()} from forwarded headers only when the
     * immediate peer is a configured trusted proxy ({@code server.tomcat.remoteip.internal-proxies},
     * default private/loopback ranges) — the framework strategy's {@code ForwardedHeaderFilter}
     * performs no such trust check (OWASP — proxy headers must only be trusted from known proxies).
     */
    private String buyerIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private String userAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        return userAgent == null || userAgent.isBlank() ? null : userAgent.trim();
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

    private void writeAssistantEvent(
            OutputStream outputStream,
            UserAssistantStreamEventResponse event
    ) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            outputStream.write(("event: " + event.type() + "\n").getBytes(StandardCharsets.UTF_8));
            outputStream.write(("data: " + payload + "\n\n").getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void writeProductSearchEvent(
            OutputStream outputStream,
            UserProductSearchStreamEventResponse event
    ) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            outputStream.write(("event: " + event.type() + "\n").getBytes(StandardCharsets.UTF_8));
            outputStream.write(("data: " + payload + "\n\n").getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
