package com.meant.api.module.user.controller;

import com.meant.api.module.user.constant.UserInventoryCategory;
import com.meant.api.module.user.controller.mapper.UserCommandMapper;
import com.meant.api.module.user.controller.request.AddUserInventoryItemRequest;
import com.meant.api.module.user.controller.request.UpdateUserInventoryItemRequest;
import com.meant.api.module.user.controller.response.UserInventoryExportResponse;
import com.meant.api.module.user.controller.response.UserInventoryItemResponse;
import com.meant.api.module.user.properties.UserCollectionProperties;
import com.meant.api.module.user.service.UserInventoryService;
import com.meant.api.module.user.service.command.DeleteUserInventoryItemCommand;
import com.meant.api.module.user.service.dto.AuthenticatedUser;
import com.meant.api.module.user.service.query.ExportUserInventoryQuery;
import com.meant.api.module.user.service.query.ListUserInventoryItemsQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Users", description = "Profile endpoints for the authenticated Supabase user")
public class UserInventoryController {

    private final UserInventoryService userInventoryService;
    private final UserCollectionProperties userCollectionProperties;

    @GetMapping("/me/inventory")
    @Operation(
            summary = "List current user inventory",
            description = "Returns owned wardrobe, pantry, home, and other items for the authenticated user."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Inventory items for the current user",
            content = @Content(array = @ArraySchema(
                    schema = @Schema(implementation = UserInventoryItemResponse.class)
            ))
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
                        UserCommandMapper.toEnsureProfileCommand(authenticatedUser),
                        new ListUserInventoryItemsQuery(
                                authenticatedUser.id(),
                                category,
                                restockOnly,
                                UserPaginationSupport.pageValue(page),
                                UserPaginationSupport.limitValue(
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
                UserCommandMapper.toEnsureProfileCommand(authenticatedUser),
                new ExportUserInventoryQuery(authenticatedUser.id())));
    }

    @PostMapping("/me/inventory")
    @Operation(
            summary = "Add an owned inventory item",
            description = "Adds a wardrobe, pantry, home, or other owned item with its uploaded photo."
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
                UserCommandMapper.toEnsureProfileCommand(authenticatedUser),
                UserCommandMapper.toCreateInventoryItemCommand(authenticatedUser.id(), request)));
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
                UserCommandMapper.toEnsureProfileCommand(authenticatedUser),
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
                UserCommandMapper.toEnsureProfileCommand(authenticatedUser),
                new DeleteUserInventoryItemCommand(authenticatedUser.id(), itemId));
    }
}
