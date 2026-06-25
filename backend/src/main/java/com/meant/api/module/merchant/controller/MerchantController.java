package com.meant.api.module.merchant.controller;

import com.meant.api.module.merchant.controller.request.MerchantSemanticProductSearchRequest;
import com.meant.api.module.merchant.controller.request.MerchantSemanticSearchRequest;
import com.meant.api.module.merchant.controller.request.MerchantIdentityCallbackRequest;
import com.meant.api.module.merchant.controller.response.MerchantIdentityAuthorizationResponse;
import com.meant.api.module.merchant.controller.response.MerchantIdentityLinkResponse;
import com.meant.api.module.merchant.controller.response.MerchantListItemResponse;
import com.meant.api.module.merchant.controller.response.MerchantProductDetailsResponse;
import com.meant.api.module.merchant.controller.response.MerchantSemanticProductSearchResponse;
import com.meant.api.module.merchant.controller.response.MerchantSemanticSearchResponse;
import com.meant.api.module.merchant.service.MerchantIdentityLinkService;
import com.meant.api.module.merchant.service.MerchantListingService;
import com.meant.api.module.merchant.service.MerchantProductDetailsService;
import com.meant.api.module.merchant.service.MerchantSemanticSearchService;
import com.meant.api.module.merchant.service.MerchantSemanticProductSearchService;
import com.meant.api.module.merchant.service.command.CompleteMerchantIdentityAuthorizationCommand;
import com.meant.api.module.merchant.service.command.RevokeMerchantIdentityLinkCommand;
import com.meant.api.module.merchant.service.command.StartMerchantIdentityAuthorizationCommand;
import com.meant.api.module.merchant.service.query.GetMerchantProductDetailsQuery;
import com.meant.api.module.merchant.service.query.SemanticMerchantSearchQuery;
import com.meant.api.module.merchant.service.query.SemanticProductSearchQuery;
import com.meant.api.module.merchant.service.query.ListMerchantIdentityLinksQuery;
import com.meant.api.module.user.service.dto.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/merchants")
@RequiredArgsConstructor
@Tag(name = "Merchants", description = "Merchant semantic search and catalog product search endpoints")
public class MerchantController {

    private final MerchantListingService merchantListingService;
    private final MerchantIdentityLinkService merchantIdentityLinkService;
    private final MerchantSemanticSearchService merchantSemanticSearchService;
    private final MerchantSemanticProductSearchService merchantSemanticProductSearchService;
    private final MerchantProductDetailsService merchantProductDetailsService;

    @GetMapping
    @Operation(
            summary = "List active merchants",
            description = "Returns active merchants available in Meant, ordered by merchant name."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Active merchants",
            content = @Content(array = @ArraySchema(
                    schema = @Schema(implementation = MerchantListItemResponse.class)
            ))
    )
    public List<MerchantListItemResponse> listActiveMerchants() {
        return merchantListingService.listActiveMerchants().stream()
                .map(MerchantListItemResponse::from)
                .toList();
    }

    @GetMapping("/identity-links")
    @Operation(
            summary = "List connected merchant accounts",
            description = "Returns the current user's merchant identity-linking connection state."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Connected merchant account states",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = MerchantIdentityLinkResponse.class)))
    )
    public List<MerchantIdentityLinkResponse> listIdentityLinks(@AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        return merchantIdentityLinkService.list(new ListMerchantIdentityLinksQuery(authenticatedUser.id())).stream()
                .map(MerchantIdentityLinkResponse::from)
                .toList();
    }

    @PostMapping("/{merchantId}/identity-link/authorization")
    @Operation(
            summary = "Start merchant account linking",
            description = "Creates a PKCE OAuth 2.0 authorization URL for merchants that advertise identity linking."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Merchant OAuth authorization URL",
            content = @Content(schema = @Schema(implementation = MerchantIdentityAuthorizationResponse.class))
    )
    public MerchantIdentityAuthorizationResponse startIdentityAuthorization(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID merchantId
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        return MerchantIdentityAuthorizationResponse.from(merchantIdentityLinkService.startAuthorization(
                new StartMerchantIdentityAuthorizationCommand(authenticatedUser.id(), merchantId)));
    }

    @PostMapping("/identity-links/oauth/callback")
    @Operation(
            summary = "Complete merchant account linking",
            description = "Stores scoped merchant OAuth tokens after the client receives an authorization code."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Connected merchant account state",
            content = @Content(schema = @Schema(implementation = MerchantIdentityLinkResponse.class))
    )
    public MerchantIdentityLinkResponse completeIdentityAuthorization(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody MerchantIdentityCallbackRequest request
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        return MerchantIdentityLinkResponse.from(merchantIdentityLinkService.completeAuthorization(
                new CompleteMerchantIdentityAuthorizationCommand(
                        authenticatedUser.id(),
                        request.state(),
                        request.code(),
                        request.issuer())));
    }

    @DeleteMapping("/identity-links/{merchantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Revoke a connected merchant account",
            description = "Revokes the merchant token when supported and removes Meant's local encrypted credentials."
    )
    @ApiResponse(responseCode = "204", description = "Merchant account connection revoked")
    public void revokeIdentityLink(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID merchantId
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        merchantIdentityLinkService.revoke(new RevokeMerchantIdentityLinkCommand(authenticatedUser.id(), merchantId));
    }

    @PostMapping("/semantic-search")
    @Operation(
            summary = "Search merchants semantically",
            description = "Searches locally embedded merchant retrieval content and reranks the merchant candidates."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Ranked merchant matches",
            content = @Content(array = @ArraySchema(
                    schema = @Schema(implementation = MerchantSemanticSearchResponse.class)
            ))
    )
    public List<MerchantSemanticSearchResponse> semanticSearch(
            @Valid @RequestBody MerchantSemanticSearchRequest request
    ) {
        return merchantSemanticSearchService.search(
                        new SemanticMerchantSearchQuery(request.query(), request.resolvedLimit())
                ).stream()
                .map(MerchantSemanticSearchResponse::from)
                .toList();
    }

    @PostMapping("/semantic-product-search")
    @Operation(
            summary = "Search merchant catalog products semantically",
            description = "Finds relevant merchants, searches their MCP catalogs, reranks product candidates, "
                    + "and enriches final products with cart-ready product details."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Ranked product matches with merchant search attempts",
            content = @Content(schema = @Schema(implementation = MerchantSemanticProductSearchResponse.class))
    )
    public MerchantSemanticProductSearchResponse semanticProductSearch(
            @Valid @RequestBody MerchantSemanticProductSearchRequest request
    ) {
        return MerchantSemanticProductSearchResponse.from(
                merchantSemanticProductSearchService.search(
                        new SemanticProductSearchQuery(
                                request.query(),
                                request.merchantId(),
                                request.merchantCandidateLimit(),
                                request.merchantLimit(),
                                request.productsPerMerchant(),
                                request.productLimit()
                        )
                )
        );
    }

    @GetMapping("/{merchantId}/product-details")
    @Operation(
            summary = "Get merchant product details",
            description = "Fetches the current product details from the merchant MCP product detail tool."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Merchant MCP product details",
            content = @Content(schema = @Schema(implementation = MerchantProductDetailsResponse.class))
    )
    public MerchantProductDetailsResponse productDetails(
            @PathVariable UUID merchantId,
            @RequestParam String productId,
            @RequestParam(required = false) String addressCountry,
            @RequestParam(required = false) String language
    ) {
        return MerchantProductDetailsResponse.from(merchantProductDetailsService.get(
                new GetMerchantProductDetailsQuery(merchantId, productId, addressCountry, language)
        ));
    }
}
