package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.exception.MerchantCartException;
import com.meant.api.module.merchant.service.dto.CartToolResponse;
import com.meant.api.module.merchant.service.dto.CartToolResult;
import com.meant.api.module.merchant.service.dto.GetCartArguments;
import com.meant.api.module.merchant.service.dto.UpdateCartArguments;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class MerchantCartClient {

    private static final String GET_CART_TOOL = "get_cart";
    private static final String UPDATE_CART_TOOL = "update_cart";

    private final MerchantMcpToolClient merchantMcpToolClient;
    private final ObjectMapper objectMapper;

    public CartToolResult updateCart(Merchant merchant, UpdateCartArguments arguments) {
        return callCartTool(merchant, UPDATE_CART_TOOL, arguments);
    }

    public CartToolResult getCart(Merchant merchant, String remoteCartId) {
        return callCartTool(merchant, GET_CART_TOOL, new GetCartArguments(remoteCartId));
    }

    private CartToolResult callCartTool(Merchant merchant, String toolName, Object arguments) {
        try {
            var result = merchantMcpToolClient.callTool(merchant, toolName, arguments);
            CartToolResponse response = objectMapper.readValue(result.contentText(), CartToolResponse.class);
            if (response == null || response.cart() == null) {
                throw new MerchantCartException("MCP cart response did not contain cart");
            }
            return new CartToolResult(result.endpoint(), result.contentText(), response);
        } catch (MerchantCartException exception) {
            throw exception;
        } catch (JacksonException exception) {
            throw new MerchantCartException("MCP cart content was not a cart response", exception);
        } catch (RuntimeException exception) {
            throw new MerchantCartException("MCP cart tool " + toolName + " failed", exception);
        }
    }
}
