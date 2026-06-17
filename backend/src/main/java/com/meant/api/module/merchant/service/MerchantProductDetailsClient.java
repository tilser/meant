package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.exception.MerchantProductDetailsException;
import com.meant.api.module.merchant.service.dto.CatalogSearchContext;
import com.meant.api.module.merchant.service.dto.MerchantSemanticSearchResult;
import com.meant.api.module.merchant.service.dto.ProductDetailsArguments;
import com.meant.api.module.merchant.service.dto.ProductDetailsResponse;
import com.meant.api.module.merchant.service.dto.ProductDetailsResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class MerchantProductDetailsClient {

    private static final String GET_PRODUCT_DETAILS_TOOL = "get_product_details";

    private final MerchantMcpToolClient merchantMcpToolClient;
    private final ObjectMapper objectMapper;

    public ProductDetailsResult getProductDetails(MerchantSemanticSearchResult merchant, String productId) {
        return getProductDetails(merchant, productId, null);
    }

    public ProductDetailsResult getProductDetails(
            MerchantSemanticSearchResult merchant,
            String productId,
            CatalogSearchContext context
    ) {
        try {
            var result = merchantMcpToolClient.callTool(
                    merchant,
                    GET_PRODUCT_DETAILS_TOOL,
                    new ProductDetailsArguments(
                            productId,
                            null,
                            context == null ? null : context.addressCountry(),
                            context == null ? null : context.language()
                    )
            );
            ProductDetailsResponse response = objectMapper.readValue(
                    result.contentText(),
                    ProductDetailsResponse.class
            );
            if (response == null || response.product() == null) {
                throw new MerchantProductDetailsException("MCP product details response did not contain product");
            }
            return new ProductDetailsResult(result.endpoint(), result.contentText(), response.product());
        } catch (MerchantProductDetailsException exception) {
            throw exception;
        } catch (JacksonException exception) {
            throw new MerchantProductDetailsException("MCP product details content was not a product response", exception);
        } catch (RuntimeException exception) {
            throw new MerchantProductDetailsException("MCP product details failed for product " + productId, exception);
        }
    }
}
