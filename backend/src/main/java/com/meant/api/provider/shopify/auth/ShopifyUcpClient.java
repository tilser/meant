package com.meant.api.provider.shopify.auth;

import com.meant.api.plugin.spi.UcpToolResponse;
import com.meant.api.provider.shopify.auth.ShopifyUcpRequestOptions;

public interface ShopifyUcpClient {

    UcpToolResponse callTool(ShopifyUcpRequestOptions options, String toolName, Object arguments);
}
