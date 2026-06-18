package com.meant.api.module.cart.service;

import com.meant.api.module.cart.exception.CartException;
import com.meant.api.module.cart.service.dto.CartToolResponse;
import com.meant.api.module.cart.service.dto.CartToolResult;
import com.meant.api.module.cart.service.dto.GetCartArguments;
import com.meant.api.module.cart.service.dto.UpdateCartArguments;
import com.meant.api.module.merchant.service.MerchantMcpToolClient;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class CartClient {

    private static final String GET_CART_TOOL = "get_cart";
    private static final String UPDATE_CART_TOOL = "update_cart";

    private final MerchantMcpToolClient merchantMcpToolClient;
    private final ObjectMapper objectMapper;

    public CartToolResult updateCart(MerchantCartProvider provider, UpdateCartArguments arguments) {
        return callCartTool(provider, UPDATE_CART_TOOL, arguments);
    }

    public CartToolResult getCart(MerchantCartProvider provider, String remoteCartId) {
        return callCartTool(provider, GET_CART_TOOL, new GetCartArguments(remoteCartId));
    }

    private CartToolResult callCartTool(MerchantCartProvider provider, String toolName, Object arguments) {
        try {
            var result = merchantMcpToolClient.callTool(provider, toolName, arguments);
            CartToolResponse response = objectMapper.readValue(result.contentText(), CartToolResponse.class);
            if (response == null || response.cart() == null) {
                throw CartException.upstream("MCP cart response did not contain cart");
            }
            return new CartToolResult(result.endpoint(), result.contentText(), response);
        } catch (CartException exception) {
            throw exception;
        } catch (JacksonException exception) {
            throw CartException.upstream("MCP cart content was not a cart response", exception);
        } catch (RuntimeException exception) {
            throw CartException.upstream("MCP cart tool " + toolName + " failed", exception);
        }
    }
}
