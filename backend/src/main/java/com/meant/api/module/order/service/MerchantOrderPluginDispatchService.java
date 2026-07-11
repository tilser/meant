package com.meant.api.module.order.service;

import static com.meant.api.common.util.CollectionUtils.safeNonNullList;

import com.meant.api.module.merchant.constant.CommerceOperation;
import com.meant.api.module.merchant.service.MerchantMcpToolClient;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.module.merchant.service.dto.MerchantMcpToolCallResult;
import com.meant.api.module.order.exception.OrderException;
import com.meant.api.plugin.order.common.dto.UcpOrderResponse;
import com.meant.api.plugin.order.common.dto.UcpOrderToolResult;
import com.meant.api.plugin.order.get.GetOrderCapability;
import com.meant.api.plugin.order.get.dto.GetOrderRequest;
import com.meant.api.plugin.spi.UcpCapability;
import com.meant.api.plugin.spi.UcpToolResponse;
import com.meant.api.plugin.support.UcpSession;
import com.meant.api.plugin.transport.registry.CapabilityRegistry;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class MerchantOrderPluginDispatchService {

    private static final int MAX_ORDER_ERROR_LENGTH = 180;

    private final MerchantMcpToolClient merchantMcpToolClient;
    private final CapabilityRegistry capabilityRegistry;
    private final ObjectMapper objectMapper;

    public UcpOrderToolResult getOrder(
            MerchantCartProvider provider,
            GetOrderRequest request,
            UcpSession session,
            String tokenType,
            String accessToken
    ) {
        provider = provider.forOperation(CommerceOperation.ORDER_READS);
        GetOrderCapability capability = capability(GetOrderCapability.TOOL_NAME, GetOrderCapability.class);
        MerchantMcpToolCallResult result = merchantMcpToolClient.callTool(
                provider,
                GetOrderCapability.TOOL_NAME,
                capability.buildArguments(request, session.activeCapabilities()),
                authorizationHeaders(tokenType, accessToken)
        );
        UcpOrderResponse response = parseOrderResponse(capability, result);
        rejectOrderProblems(request.orderId(), response);
        session.acceptNegotiatedCapabilities(result.negotiatedCapabilities());
        return orderResult(result, response);
    }

    private UcpOrderResponse parseOrderResponse(
            UcpCapability<?, UcpOrderResponse> capability,
            MerchantMcpToolCallResult result
    ) {
        UcpOrderResponse response = capability.parseResponse(toolResponse(result));
        if (response == null) {
            throw OrderException.upstream("UCP get_order response was empty");
        }
        return response;
    }

    private void rejectOrderProblems(String orderId, UcpOrderResponse response) {
        UcpOrderResponse.OrderError error = firstError(response.errors());
        if (error != null) {
            if (error.isNotFound()) {
                throw OrderException.notFound("Order not found: " + orderId);
            }
            throw OrderException.rejected(safeOrderErrorMessage(error.message()));
        }

        UcpOrderResponse.OrderMessage message = firstErrorMessage(response);
        if (message != null) {
            if (message.isNotFound()) {
                throw OrderException.notFound("Order not found: " + orderId);
            }
            throw OrderException.rejected(safeOrderErrorMessage(message.message()));
        }

        if (response.order() == null) {
            throw OrderException.upstream("UCP order response did not contain order");
        }
    }

    private UcpOrderResponse.OrderError firstError(List<UcpOrderResponse.OrderError> errors) {
        if (errors == null || errors.isEmpty()) {
            return null;
        }
        return errors.stream()
                .filter(error -> error != null && (error.isNotFound() || hasText(error.message())))
                .findFirst()
                .orElse(null);
    }

    private UcpOrderResponse.OrderMessage firstErrorMessage(UcpOrderResponse response) {
        return Stream.concat(
                        safeNonNullList(response.messages()).stream(),
                        Stream.<UcpOrderResponse.OrderMessage>empty()
                )
                .filter(UcpOrderResponse.OrderMessage::isError)
                .findFirst()
                .orElse(null);
    }

    private UcpOrderToolResult orderResult(MerchantMcpToolCallResult result, UcpOrderResponse response) {
        return new UcpOrderToolResult(result.endpoint(), rawResponse(result, response), response);
    }

    private String rawResponse(MerchantMcpToolCallResult result, UcpOrderResponse response) {
        if (result.contentText() != null && !result.contentText().isBlank()) {
            return result.contentText();
        }
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JacksonException exception) {
            throw OrderException.upstream("Could not serialize UCP order response", exception);
        }
    }

    private UcpToolResponse toolResponse(MerchantMcpToolCallResult result) {
        return new UcpToolResponse(
                result.contentText(),
                result.structuredContent(),
                result.negotiatedCapabilities()
        );
    }

    private <T extends UcpCapability<?, ?>> T capability(String toolName, Class<T> type) {
        UcpCapability<?, ?> capability = capabilityRegistry.capabilityForTool(toolName);
        if (!type.isInstance(capability)) {
            throw new IllegalStateException(
                    "UCP tool " + toolName + " was registered to " + capability.getClass().getSimpleName()
            );
        }
        return type.cast(capability);
    }

    private Map<String, String> authorizationHeaders(String tokenType, String accessToken) {
        if (!hasText(accessToken)) {
            throw OrderException.rejected("A merchant account connection is required to read this order.");
        }
        String scheme = hasText(tokenType) ? tokenType.trim() : "Bearer";
        return Map.of("Authorization", scheme + " " + accessToken.trim());
    }

    private String safeOrderErrorMessage(String message) {
        String trimmed = hasText(message) ? message.trim() : "The merchant rejected this order operation.";
        return trimmed.length() <= MAX_ORDER_ERROR_LENGTH
                ? trimmed
                : trimmed.substring(0, MAX_ORDER_ERROR_LENGTH);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
