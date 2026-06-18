package com.meant.api.module.cart.service;

import com.meant.api.module.cart.exception.CartException;
import com.meant.api.module.cart.service.dto.CartToolResponse;
import com.meant.api.module.cart.service.dto.CartToolResult;
import com.meant.api.module.cart.service.dto.GetCartArguments;
import com.meant.api.module.cart.service.dto.UpdateCartArguments;
import com.meant.api.module.merchant.service.MerchantMcpToolClient;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class CartClient {

    private static final String GET_CART_TOOL = "get_cart";
    private static final String UPDATE_CART_TOOL = "update_cart";
    private static final int MAX_CART_ERROR_LENGTH = 180;

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
            rejectCartErrors(response);
            rejectInapplicableCodes(arguments, response);
            return new CartToolResult(result.endpoint(), result.contentText(), response);
        } catch (CartException exception) {
            throw exception;
        } catch (JacksonException exception) {
            throw CartException.upstream("MCP cart content was not a cart response", exception);
        } catch (RuntimeException exception) {
            throw CartException.upstream("MCP cart tool " + toolName + " failed", exception);
        }
    }

    private void rejectCartErrors(CartToolResponse response) {
        String message = firstCartErrorMessage(response.errors());
        if (message != null) {
            throw CartException.rejected(message);
        }
    }

    private String firstCartErrorMessage(List<CartToolResponse.CartError> errors) {
        if (errors == null || errors.isEmpty()) {
            return null;
        }
        return errors.stream()
                .map(CartToolResponse.CartError::message)
                .filter(message -> message != null && !message.isBlank())
                .findFirst()
                .map(this::safeCartErrorMessage)
                .orElse("The merchant rejected this cart update.");
    }

    private void rejectInapplicableCodes(Object arguments, CartToolResponse response) {
        if (!(arguments instanceof UpdateCartArguments updateCartArguments)) {
            return;
        }
        rejectInapplicableCodes(
                "Discount code",
                updateCartArguments.discountCodes(),
                response.cart().discountCodes()
        );
        rejectInapplicableCodes(
                "Gift card code",
                updateCartArguments.giftCardCodes(),
                response.cart().giftCardCodes()
        );
    }

    private void rejectInapplicableCodes(
            String label,
            Collection<String> submittedCodes,
            List<CartToolResponse.AppliedCode> responseCodes
    ) {
        if (submittedCodes == null || submittedCodes.isEmpty() || responseCodes == null || responseCodes.isEmpty()) {
            return;
        }

        Set<String> normalizedSubmittedCodes = submittedCodes.stream()
                .filter(submittedCode -> submittedCode != null && !submittedCode.isBlank())
                .map(submittedCode -> submittedCode.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (normalizedSubmittedCodes.isEmpty()) {
            return;
        }

        List<String> rejectedCodes = responseCodes.stream()
                .filter(appliedCode -> Boolean.FALSE.equals(appliedCode.applicable()))
                .map(CartToolResponse.AppliedCode::code)
                .filter(responseCode -> responseCode != null && !responseCode.isBlank())
                .filter(responseCode -> normalizedSubmittedCodes.contains(responseCode.toLowerCase(Locale.ROOT)))
                .toList();
        if (!rejectedCodes.isEmpty()) {
            throw CartException.rejected(label + " " + rejectedCodes.getFirst() + " was not accepted by the merchant.");
        }
    }

    private String safeCartErrorMessage(String message) {
        String trimmed = message.trim();
        return trimmed.length() <= MAX_CART_ERROR_LENGTH
                ? trimmed
                : trimmed.substring(0, MAX_CART_ERROR_LENGTH);
    }
}
