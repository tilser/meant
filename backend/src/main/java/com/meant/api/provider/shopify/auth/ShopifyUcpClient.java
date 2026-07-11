package com.meant.api.provider.shopify.auth;

import com.meant.api.plugin.spi.UcpToolResponse;
import com.meant.api.provider.shopify.auth.ShopifyUcpRequestOptions;
import java.util.Map;

public interface ShopifyUcpClient {

    UcpToolResponse callTool(ShopifyUcpRequestOptions options, String toolName, Object arguments);

    default UcpToolResponse callTool(
            ShopifyUcpRequestOptions options, String toolName, Object arguments, Map<String, String> headers) {
        return callTool(options, toolName, arguments);
    }
}
