package com.meant.api.plugin.transport.client;

import com.meant.api.plugin.spi.UcpToolResponse;
import com.meant.api.plugin.transport.dto.ShopifyUcpRequestOptions;

public interface ShopifyUcpClient {

    UcpToolResponse callTool(ShopifyUcpRequestOptions options, String toolName, Object arguments);
}
