package com.meant.api.module.user.controller;

import com.meant.api.common.security.PermanentAccountRequired;
import com.meant.api.module.user.constant.UserProductDiscoverySortDirection;
import com.meant.api.module.user.constant.UserProductDiscoverySortField;
import com.meant.api.module.user.controller.mapper.UserCommandMapper;
import com.meant.api.module.user.controller.response.UserPopularProductSearchResponse;
import com.meant.api.module.user.controller.response.UserProductDiscoveryResponse;
import com.meant.api.module.user.controller.response.UserProductSearchSuggestionsResponse;
import com.meant.api.module.user.service.UserProductDiscoveryService;
import com.meant.api.module.user.service.UserProductSearchEventService;
import com.meant.api.module.user.service.UserProductSearchSuggestionService;
import com.meant.api.module.user.service.dto.AuthenticatedUser;
import com.meant.api.module.user.service.query.GetUserProductDiscoveryQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Users", description = "Profile endpoints for the authenticated Supabase user")
public class UserProductSearchController {

    private final UserProductDiscoveryService userProductDiscoveryService;
    private final UserProductSearchEventService userProductSearchEventService;
    private final UserProductSearchSuggestionService userProductSearchSuggestionService;

    @GetMapping("/me/product-search-suggestions")
    @PermanentAccountRequired
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
                UserCommandMapper.toEnsureProfileCommand(authenticatedUser)));
    }

    @GetMapping("/me/product-discovery")
    @PermanentAccountRequired
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
                UserCommandMapper.toEnsureProfileCommand(authenticatedUser),
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
            content = @Content(array = @ArraySchema(
                    schema = @Schema(implementation = UserPopularProductSearchResponse.class)
            ))
    )
    public List<UserPopularProductSearchResponse> popularProductSearches(@AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser.fromJwt(jwt);
        return userProductSearchEventService.popular(Instant.now()).stream()
                .map(UserPopularProductSearchResponse::from)
                .toList();
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

}
