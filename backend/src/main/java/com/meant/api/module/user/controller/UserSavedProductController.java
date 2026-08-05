package com.meant.api.module.user.controller;

import com.meant.api.common.security.PermanentAccountRequired;
import com.meant.api.module.user.controller.mapper.UserCommandMapper;
import com.meant.api.module.user.controller.request.SaveUserProductRequest;
import com.meant.api.module.user.controller.response.UserSavedProductResponse;
import com.meant.api.module.user.properties.UserCollectionProperties;
import com.meant.api.module.user.service.UserSavedProductService;
import com.meant.api.module.user.service.command.RemoveSavedProductCommand;
import com.meant.api.module.user.service.dto.AuthenticatedUser;
import com.meant.api.module.user.service.query.GetSavedProductQuery;
import com.meant.api.module.user.service.query.ListSavedProductsQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
@PermanentAccountRequired
public class UserSavedProductController {

    private final UserSavedProductService userSavedProductService;
    private final UserCollectionProperties userCollectionProperties;

    @GetMapping("/me/saved-products")
    @Operation(
            summary = "List saved products",
            description = "Returns the current user's saved product snapshots."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Saved products for the current user",
            content = @Content(array = @ArraySchema(
                    schema = @Schema(implementation = UserSavedProductResponse.class)
            ))
    )
    public List<UserSavedProductResponse> savedProducts(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer limit
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        return userSavedProductService.list(
                        UserCommandMapper.toEnsureProfileCommand(authenticatedUser),
                        new ListSavedProductsQuery(
                                authenticatedUser.id(),
                                UserPaginationSupport.pageValue(page),
                                UserPaginationSupport.limitValue(
                                        limit,
                                        userCollectionProperties.savedProducts().defaultLimit(),
                                        userCollectionProperties.savedProducts().maxLimit())))
                .stream()
                .map(UserSavedProductResponse::from)
                .toList();
    }

    @GetMapping("/me/saved-products/detail")
    @Operation(
            summary = "Get a saved product",
            description = "Loads the current user's durable saved-product reference and rehydrates its current "
                    + "merchant offer without relying on the original search session."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Saved product with current rehydrated facts when available",
            content = @Content(schema = @Schema(implementation = UserSavedProductResponse.class))
    )
    @ApiResponse(responseCode = "404", description = "Saved product was not found")
    public UserSavedProductResponse savedProduct(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String productKey
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        return UserSavedProductResponse.from(userSavedProductService.get(
                UserCommandMapper.toEnsureProfileCommand(authenticatedUser),
                new GetSavedProductQuery(authenticatedUser.id(), productKey)
        ));
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
                UserCommandMapper.toEnsureProfileCommand(authenticatedUser),
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
                UserCommandMapper.toEnsureProfileCommand(authenticatedUser),
                new RemoveSavedProductCommand(authenticatedUser.id(), productKey));
    }
}
