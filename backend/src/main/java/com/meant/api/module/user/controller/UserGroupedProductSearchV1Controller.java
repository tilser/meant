package com.meant.api.module.user.controller;

import com.meant.api.module.user.controller.mapper.UserCommandMapper;
import com.meant.api.module.user.controller.request.UserCanonicalProductRehydrationRequest;
import com.meant.api.module.user.controller.request.UserSimilarProductSearchRequest;
import com.meant.api.module.user.controller.request.UserProductSearchRequest;
import com.meant.api.module.user.controller.response.UserGroupedProductSearchV1Response;
import com.meant.api.module.user.controller.response.UserSimilarProductSearchV1Response;
import com.meant.api.module.user.controller.response.UserCanonicalProductRehydrationV1Response;
import com.meant.api.module.user.controller.response.UserFederatedProductSearchStreamEventResponse;
import com.meant.api.module.user.controller.response.UserCanonicalProductDetailV1Response;
import com.meant.api.module.user.properties.UserProductSearchProperties;
import com.meant.api.module.user.service.UserFederatedProductSearchStreamService;
import com.meant.api.module.user.service.UserGroupedProductSearchService;
import com.meant.api.module.user.service.UserQualifiedProductSearchResolver;
import com.meant.api.module.user.service.UserCanonicalProductDetailService;
import com.meant.api.module.user.service.UserSimilarProductSearchService;
import com.meant.api.module.user.service.command.SearchUserProductsCommand;
import com.meant.api.module.user.service.command.SearchSimilarUserProductsCommand;
import com.meant.api.module.user.service.dto.AuthenticatedUser;
import com.meant.api.module.user.service.dto.UserQualifiedProductSearchInput;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.service.query.GetUserCanonicalProductDetailQuery;
import com.meant.api.module.user.service.query.RehydrateUserCanonicalProductsQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import java.util.Objects;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Users", description = "Profile endpoints for the authenticated Supabase user")
public class UserGroupedProductSearchV1Controller {

    private final UserGroupedProductSearchService userGroupedProductSearchService;
    private final UserSimilarProductSearchService userSimilarProductSearchService;
    private final UserCanonicalProductDetailService userCanonicalProductDetailService;
    private final UserFederatedProductSearchStreamService userFederatedProductSearchStreamService;
    private final UserProductSearchProperties userProductSearchProperties;
    private final UserStreamEventWriter userStreamEventWriter;
    private final UserQualifiedProductSearchResolver qualifiedSearchResolver;

    @PostMapping("/me/product-searches")
    @Operation(
            operationId = "searchGroupedProductsV1",
            summary = "Search grouped canonical products for the current user",
            description = "Executes a server-issued READY qualification plan against eligible catalog providers, "
                    + "then returns provider-neutral products with exact merchant offers and provenance."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Version 1 grouped product search results",
            content = @Content(schema = @Schema(implementation = UserGroupedProductSearchV1Response.class))
    )
    public UserGroupedProductSearchV1Response searchProducts(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UserProductSearchRequest request,
            HttpServletRequest httpRequest
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        UserQualifiedProductSearchInput qualified = qualifiedSearch(authenticatedUser, request);
        return UserGroupedProductSearchV1Response.from(userGroupedProductSearchService.search(
                UserCommandMapper.toEnsureProfileCommand(authenticatedUser),
                new SearchUserProductsCommand(
                        authenticatedUser.id(),
                        qualified.effectiveQuery(),
                        qualified.merchantId(),
                        httpRequest.getRemoteAddr(),
                        userAgent(httpRequest),
                        request.offset(),
                        request.limit()
                ),
                qualified.filters()
        ));
    }

    @GetMapping("/me/products/{canonicalProductKey}")
    @Operation(
            operationId = "getCanonicalProductDetailV1",
            summary = "Get current detail for a grouped canonical product",
            description = "Resolves a server-issued Meant canonical product key and optional exact offer key from the "
                    + "authenticated user's live search session or durable identifier-only references, then "
                    + "batch-rehydrates current commercial facts. "
                    + "Provider endpoints, merchant identities, prices, routing, and checkout URLs are never accepted."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Canonical product detail with recommended, selected, and alternative offers",
            content = @Content(schema = @Schema(implementation = UserCanonicalProductDetailV1Response.class))
    )
    public UserCanonicalProductDetailV1Response getProductDetail(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String canonicalProductKey,
            @RequestParam(required = false) String selectedOfferKey
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        return UserCanonicalProductDetailV1Response.from(userCanonicalProductDetailService.get(
                UserCommandMapper.toEnsureProfileCommand(authenticatedUser),
                new GetUserCanonicalProductDetailQuery(
                        authenticatedUser.id(), canonicalProductKey, selectedOfferKey)
        ));
    }

    @PostMapping("/me/products:rehydrate")
    @Operation(
            operationId = "rehydrateCanonicalProductsV1",
            summary = "Rehydrate canonical products from durable chat references",
            description = "Deduplicates authenticated-user server-issued canonical product keys in first-seen order, "
                    + "resolves live-session or durable identifier-only references, and batch-rehydrates current "
                    + "commercial facts. Unknown, unauthorized, stale, and unavailable keys are reported uniformly."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Available current products and uniformly unavailable canonical keys",
            content = @Content(schema = @Schema(implementation = UserCanonicalProductRehydrationV1Response.class))
    )
    public UserCanonicalProductRehydrationV1Response rehydrateProducts(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UserCanonicalProductRehydrationRequest request
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        return UserCanonicalProductRehydrationV1Response.from(userCanonicalProductDetailService.rehydrate(
                UserCommandMapper.toEnsureProfileCommand(authenticatedUser),
                new RehydrateUserCanonicalProductsQuery(
                        authenticatedUser.id(), request.canonicalProductKeys())
        ));
    }

    @PostMapping("/me/products/{canonicalProductKey}/similar")
    @Operation(
            operationId = "searchSimilarProductsV1",
            summary = "Search for products similar to a grouped canonical product",
            description = "Resolves the authenticated user's server-issued canonical product key to a trusted "
                    + "product-level item reference, then narrows provider similarity results with the originating "
                    + "query and, when supplied, its authenticated READY qualification filters. Returns one fixed "
                    + "page of up to 20 products with no continuation. Provider identifiers, merchant routing, "
                    + "endpoints, and image content are never accepted."
    )
    @ApiResponse(
            responseCode = "200",
            description = "One fixed page of up to 20 grouped similar products excluding the reference product",
            content = @Content(schema = @Schema(implementation = UserSimilarProductSearchV1Response.class))
    )
    public UserSimilarProductSearchV1Response searchSimilarProducts(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String canonicalProductKey,
            @Valid @RequestBody UserSimilarProductSearchRequest request,
            HttpServletRequest httpRequest
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        return UserSimilarProductSearchV1Response.from(userSimilarProductSearchService.search(
                UserCommandMapper.toEnsureProfileCommand(authenticatedUser),
                new SearchSimilarUserProductsCommand(
                        authenticatedUser.id(),
                        canonicalProductKey,
                        request.query(),
                        request.qualificationId(),
                        httpRequest.getRemoteAddr(),
                        userAgent(httpRequest)
                )
        ));
    }

    @PostMapping(value = "/me/product-searches:stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            operationId = "streamFederatedProductsV1",
            summary = "Stream federated product candidates for the current user",
            description = "Streams provider-neutral candidates, source completions, scoped degradations, and exactly "
                    + "one request terminal event. Every candidate retains provider and discovery provenance."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Federated discovery event stream",
            content = @Content(schema = @Schema(implementation = UserFederatedProductSearchStreamEventResponse.class))
    )
    public SseEmitter streamProducts(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UserProductSearchRequest request,
            HttpServletRequest httpRequest
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        UserQualifiedProductSearchInput qualified = qualifiedSearch(authenticatedUser, request);
        SearchUserProductsCommand command = new SearchUserProductsCommand(
                authenticatedUser.id(),
                qualified.effectiveQuery(),
                qualified.merchantId(),
                httpRequest.getRemoteAddr(),
                userAgent(httpRequest),
                request.offset(),
                request.limit()
        );
        SseEmitter emitter = new SseEmitter(userProductSearchProperties.streamTimeout().toMillis());
        UserSseSession<UserFederatedProductSearchStreamEventResponse> session = new UserSseSession<>(
                emitter,
                userProductSearchProperties.streamQueueCapacity(),
                authenticatedUser.id(),
                command.merchantId(),
                userStreamEventWriter::writeFederatedProductSearchEvent,
                event -> event.terminalStatus() != null,
                ignored -> UserFederatedProductSearchStreamEventResponse.error()
        );
        session.start(() -> userFederatedProductSearchStreamService.stream(
                UserCommandMapper.toEnsureProfileCommand(authenticatedUser),
                command,
                qualified.filters(),
                event -> session.send(UserFederatedProductSearchStreamEventResponse.from(event))
        ));
        return emitter;
    }

    private String userAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        return userAgent == null || userAgent.isBlank() ? null : userAgent.trim();
    }

    private UserQualifiedProductSearchInput qualifiedSearch(
            AuthenticatedUser authenticatedUser,
            UserProductSearchRequest request
    ) {
        UserQualifiedProductSearchInput qualified = qualifiedSearchResolver.resolve(
                authenticatedUser.id(), request.qualificationId());
        if (!Objects.equals(qualified.merchantId(), request.merchantId())) {
            throw new UserException("Product-search qualification merchant scope does not match the request");
        }
        return qualified;
    }
}
