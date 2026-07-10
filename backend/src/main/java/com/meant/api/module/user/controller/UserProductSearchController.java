package com.meant.api.module.user.controller;

import com.meant.api.module.user.constant.UserProductDiscoverySortDirection;
import com.meant.api.module.user.constant.UserProductDiscoverySortField;
import com.meant.api.module.user.controller.mapper.UserCommandMapper;
import com.meant.api.module.user.controller.request.UserProductSearchRequest;
import com.meant.api.module.user.controller.response.UserPopularProductSearchResponse;
import com.meant.api.module.user.controller.response.UserProductDiscoveryResponse;
import com.meant.api.module.user.controller.response.UserProductSearchResponse;
import com.meant.api.module.user.controller.response.UserProductSearchSuggestionsResponse;
import com.meant.api.module.user.controller.response.UserProductSearchStreamEventResponse;
import com.meant.api.module.user.properties.UserProductSearchProperties;
import com.meant.api.module.user.service.UserProductDiscoveryService;
import com.meant.api.module.user.service.UserProductSearchEventService;
import com.meant.api.module.user.service.UserProductSearchService;
import com.meant.api.module.user.service.UserProductSearchSuggestionService;
import com.meant.api.module.user.service.command.SearchUserProductsCommand;
import com.meant.api.module.user.service.dto.AuthenticatedUser;
import com.meant.api.module.user.service.query.GetUserProductDiscoveryQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Users", description = "Profile endpoints for the authenticated Supabase user")
public class UserProductSearchController {

    private final UserProductDiscoveryService userProductDiscoveryService;
    private final UserProductSearchEventService userProductSearchEventService;
    private final UserProductSearchService userProductSearchService;
    private final UserProductSearchSuggestionService userProductSearchSuggestionService;
    private final UserProductSearchProperties userProductSearchProperties;
    private final UserStreamEventWriter userStreamEventWriter;

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
                UserCommandMapper.toEnsureProfileCommand(authenticatedUser),
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
            description = "Streams Discovery Agent catalog candidates, Meant Curator score and order updates, "
                    + "and final pagination metadata as soon as each piece is available."
    )
    public SseEmitter streamSearchProducts(
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
        SseEmitter emitter = new SseEmitter(userProductSearchProperties.streamTimeout().toMillis());
        UserSseSession<UserProductSearchStreamEventResponse> session = new UserSseSession<>(
                emitter,
                userProductSearchProperties.streamQueueCapacity(),
                authenticatedUser.id(),
                command.merchantId(),
                userStreamEventWriter::writeProductSearchEvent,
                event -> "done".equals(event.type()) || "error".equals(event.type()),
                ignored -> UserProductSearchStreamEventResponse.error(
                        "Product search failed. Please try again."
                )
        );
        session.start(() -> userProductSearchService.stream(
                UserCommandMapper.toEnsureProfileCommand(authenticatedUser),
                command,
                event -> session.send(UserProductSearchStreamEventResponse.from(event))
        ));
        return emitter;
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
                UserCommandMapper.toEnsureProfileCommand(authenticatedUser)));
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

    /**
     * Resolves the client IP without parsing client-supplied forwarding headers in application code.
     * With the default {@code server.forward-headers-strategy=none}, {@code getRemoteAddr()} is the
     * real socket peer and attacker-set {@code X-Forwarded-For}/{@code Forwarded} headers are ignored
     * entirely. Behind a trusted reverse proxy, set the strategy to {@code native} so Tomcat's
     * {@code RemoteIpValve} rewrites {@code getRemoteAddr()} from forwarded headers only when the
     * immediate peer is a configured trusted proxy ({@code server.tomcat.remoteip.internal-proxies},
     * default private/loopback ranges) - the framework strategy's {@code ForwardedHeaderFilter}
     * performs no such trust check (OWASP - proxy headers must only be trusted from known proxies).
     */
    private String buyerIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private String userAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        return userAgent == null || userAgent.isBlank() ? null : userAgent.trim();
    }
}
