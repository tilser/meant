package com.meant.api.module.merchant.controller;

import com.meant.api.module.merchant.controller.request.MerchantSemanticSearchRequest;
import com.meant.api.module.merchant.controller.request.MerchantSemanticProductSearchRequest;
import com.meant.api.module.merchant.controller.response.MerchantSemanticSearchResponse;
import com.meant.api.module.merchant.controller.response.MerchantSemanticProductSearchResponse;
import com.meant.api.module.merchant.service.MerchantSemanticSearchService;
import com.meant.api.module.merchant.service.MerchantSemanticProductSearchService;
import com.meant.api.module.merchant.service.query.SemanticMerchantSearchQuery;
import com.meant.api.module.merchant.service.query.SemanticProductSearchQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/merchants")
@RequiredArgsConstructor
@Tag(name = "Merchants", description = "Merchant semantic search and catalog product search endpoints")
public class MerchantController {

    private final MerchantSemanticSearchService merchantSemanticSearchService;
    private final MerchantSemanticProductSearchService merchantSemanticProductSearchService;

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
                                request.merchantCandidateLimit(),
                                request.merchantLimit(),
                                request.productsPerMerchant(),
                                request.productLimit()
                        )
                )
        );
    }
}
