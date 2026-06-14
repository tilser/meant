package com.meant.api.module.merchant.controller;

import com.meant.api.module.merchant.controller.request.MerchantSemanticSearchRequest;
import com.meant.api.module.merchant.controller.response.MerchantSemanticSearchResponse;
import com.meant.api.module.merchant.service.MerchantSemanticSearchService;
import com.meant.api.module.merchant.service.query.SemanticMerchantSearchQuery;
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
public class MerchantController {

    private final MerchantSemanticSearchService merchantSemanticSearchService;

    @PostMapping("/semantic-search")
    public List<MerchantSemanticSearchResponse> semanticSearch(
            @Valid @RequestBody MerchantSemanticSearchRequest request
    ) {
        return merchantSemanticSearchService.search(
                        new SemanticMerchantSearchQuery(request.query(), request.resolvedLimit())
                ).stream()
                .map(MerchantSemanticSearchResponse::from)
                .toList();
    }
}
