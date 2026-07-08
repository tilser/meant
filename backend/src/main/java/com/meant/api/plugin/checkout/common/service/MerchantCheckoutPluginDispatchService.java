package com.meant.api.plugin.checkout.common.service;

import static com.meant.api.common.util.CollectionUtils.safeNonNullList;

import com.meant.api.module.cart.exception.CartException;
import com.meant.api.module.merchant.service.MerchantMcpToolClient;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.module.merchant.service.dto.MerchantMcpToolCallResult;
import com.meant.api.plugin.checkout.cancel.CancelCheckoutCapability;
import com.meant.api.plugin.checkout.cancel.dto.CancelCheckoutRequest;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutToolResult;
import com.meant.api.plugin.checkout.complete.CompleteCheckoutCapability;
import com.meant.api.plugin.checkout.complete.dto.CompleteCheckoutRequest;
import com.meant.api.plugin.checkout.create.CreateCheckoutCapability;
import com.meant.api.plugin.checkout.create.dto.CreateCheckoutRequest;
import com.meant.api.plugin.checkout.get.GetCheckoutCapability;
import com.meant.api.plugin.checkout.get.dto.GetCheckoutRequest;
import com.meant.api.plugin.checkout.update.UpdateCheckoutCapability;
import com.meant.api.plugin.checkout.update.dto.UpdateCheckoutRequest;
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
public class MerchantCheckoutPluginDispatchService {

    private static final int MAX_CHECKOUT_ERROR_LENGTH = 180;

    private final MerchantMcpToolClient merchantMcpToolClient;
    private final CapabilityRegistry capabilityRegistry;
    private final ObjectMapper objectMapper;

    public UcpCheckoutToolResult createCheckout(
            MerchantCartProvider provider,
            CreateCheckoutRequest request,
            UcpSession session
    ) {
        CreateCheckoutCapability capability = capability(
                CreateCheckoutCapability.TOOL_NAME,
                CreateCheckoutCapability.class
        );
        MerchantMcpToolCallResult result = merchantMcpToolClient.callToolReturningJsonToolErrors(
                provider,
                CreateCheckoutCapability.TOOL_NAME,
                capability.buildArguments(request, session.activeCapabilities())
        );
        UcpCheckoutResponse response = parseCheckoutResponse(capability, result, "create checkout");
        rejectCheckoutProblems("Cart not found: " + request.cartId(), response);
        updateSession(session, result, response);
        return checkoutResult(result, response);
    }

    public UcpCheckoutToolResult getCheckout(
            MerchantCartProvider provider,
            GetCheckoutRequest request,
            UcpSession session
    ) {
        GetCheckoutCapability capability = capability(GetCheckoutCapability.TOOL_NAME, GetCheckoutCapability.class);
        MerchantMcpToolCallResult result = merchantMcpToolClient.callToolReturningJsonToolErrors(
                provider,
                GetCheckoutCapability.TOOL_NAME,
                capability.buildArguments(request, session.activeCapabilities())
        );
        UcpCheckoutResponse response = parseCheckoutResponse(capability, result, "get checkout");
        rejectCheckoutProblems("Checkout not found: " + request.checkoutId(), response);
        updateSession(session, result, response);
        return checkoutResult(result, response);
    }

    public UcpCheckoutToolResult updateCheckout(
            MerchantCartProvider provider,
            UpdateCheckoutRequest request,
            UcpSession session
    ) {
        UpdateCheckoutCapability capability = capability(
                UpdateCheckoutCapability.TOOL_NAME,
                UpdateCheckoutCapability.class
        );
        MerchantMcpToolCallResult result = merchantMcpToolClient.callToolReturningJsonToolErrors(
                provider,
                UpdateCheckoutCapability.TOOL_NAME,
                capability.buildArguments(request, session.activeCapabilities())
        );
        UcpCheckoutResponse response = parseCheckoutResponse(capability, result, "update checkout");
        rejectCheckoutProblems("Checkout not found: " + request.checkoutId(), response);
        updateSession(session, result, response);
        return checkoutResult(result, response);
    }

    public UcpCheckoutToolResult completeCheckout(
            MerchantCartProvider provider,
            CompleteCheckoutRequest request,
            UcpSession session,
            Map<String, String> signedHeaders
    ) {
        CompleteCheckoutCapability capability = capability(
                CompleteCheckoutCapability.TOOL_NAME,
                CompleteCheckoutCapability.class
        );
        MerchantMcpToolCallResult result = merchantMcpToolClient.callToolReturningJsonToolErrors(
                provider,
                CompleteCheckoutCapability.TOOL_NAME,
                capability.buildArguments(request, session.activeCapabilities()),
                signedHeaders
        );
        UcpCheckoutResponse response = parseCheckoutResponse(capability, result, "complete checkout");
        updateSessionIfCheckoutPresent(session, result, response);
        return checkoutResult(result, response);
    }

    public UcpCheckoutToolResult cancelCheckout(
            MerchantCartProvider provider,
            CancelCheckoutRequest request,
            UcpSession session,
            Map<String, String> signedHeaders
    ) {
        CancelCheckoutCapability capability = capability(CancelCheckoutCapability.TOOL_NAME, CancelCheckoutCapability.class);
        MerchantMcpToolCallResult result = merchantMcpToolClient.callToolReturningJsonToolErrors(
                provider,
                CancelCheckoutCapability.TOOL_NAME,
                capability.buildArguments(request, session.activeCapabilities()),
                signedHeaders
        );
        UcpCheckoutResponse response = parseCheckoutResponse(capability, result, "cancel checkout");
        updateSessionIfCheckoutPresent(session, result, response);
        return checkoutResult(result, response);
    }

    private UcpCheckoutResponse parseCheckoutResponse(
            UcpCapability<?, UcpCheckoutResponse> capability,
            MerchantMcpToolCallResult result,
            String operation
    ) {
        UcpCheckoutResponse response = capability.parseResponse(toolResponse(result));
        if (response == null) {
            throw CartException.upstream("UCP " + operation + " response was empty");
        }
        return response;
    }

    private void rejectCheckoutProblems(String notFoundMessage, UcpCheckoutResponse response) {
        UcpCheckoutResponse.CheckoutError error = firstError(response.errors());
        if (error != null) {
            if (error.isNotFound()) {
                throw CartException.notFound(notFoundMessage);
            }
            if (response.resolvedCheckout() == null) {
                throw CartException.rejected(safeCheckoutErrorMessage(error.message()));
            }
        }

        UcpCheckoutResponse.CheckoutMessage message = firstBlockingErrorMessage(response);
        if (message != null) {
            if (message.isNotFound()) {
                throw CartException.notFound(notFoundMessage);
            }
            throw CartException.rejected(safeCheckoutErrorMessage(message.message()));
        }

        if (response.resolvedCheckout() == null) {
            throw CartException.upstream("UCP checkout response did not contain checkout");
        }
    }

    private UcpCheckoutResponse.CheckoutError firstError(List<UcpCheckoutResponse.CheckoutError> errors) {
        if (errors == null || errors.isEmpty()) {
            return null;
        }
        return errors.stream()
                .filter(error -> error != null && (error.isNotFound() || hasText(error.message())))
                .findFirst()
                .orElse(null);
    }

    private UcpCheckoutResponse.CheckoutMessage firstBlockingErrorMessage(UcpCheckoutResponse response) {
        return Stream.concat(
                        safeNonNullList(response.messages()).stream(),
                        response.resolvedCheckout() == null
                                ? Stream.empty()
                                : safeNonNullList(response.resolvedCheckout().messages()).stream()
                )
                .filter(message -> message.isNotFound() || (message.isError() && !message.isRecoverable()))
                .findFirst()
                .orElse(null);
    }

    private void updateSession(
            UcpSession session,
            MerchantMcpToolCallResult result,
            UcpCheckoutResponse response
    ) {
        session.acceptNegotiatedCapabilities(result.negotiatedCapabilities());
        UcpCheckoutResponse.Checkout checkout = response.resolvedCheckout();
        session.updateCheckoutState(checkout.id(), checkout.expiresAt(), handoffUrl(checkout));
    }

    private void updateSessionIfCheckoutPresent(
            UcpSession session,
            MerchantMcpToolCallResult result,
            UcpCheckoutResponse response
    ) {
        session.acceptNegotiatedCapabilities(result.negotiatedCapabilities());
        UcpCheckoutResponse.Checkout checkout = response.resolvedCheckout();
        if (checkout != null) {
            session.updateCheckoutState(checkout.id(), checkout.expiresAt(), handoffUrl(checkout));
        }
    }

    private UcpCheckoutToolResult checkoutResult(
            MerchantMcpToolCallResult result,
            UcpCheckoutResponse response
    ) {
        return new UcpCheckoutToolResult(result.endpoint(), rawResponse(result, response), response);
    }

    private String rawResponse(MerchantMcpToolCallResult result, UcpCheckoutResponse response) {
        if (result.contentText() != null && !result.contentText().isBlank()) {
            return result.contentText();
        }
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JacksonException exception) {
            throw CartException.upstream("Could not serialize UCP checkout response", exception);
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

    private String handoffUrl(UcpCheckoutResponse.Checkout checkout) {
        if (hasText(checkout.continueUrl())) {
            return checkout.continueUrl();
        }
        return checkout.checkoutUrl();
    }

    private String safeCheckoutErrorMessage(String message) {
        String trimmed = hasText(message) ? message.trim() : "The merchant rejected this checkout operation.";
        return trimmed.length() <= MAX_CHECKOUT_ERROR_LENGTH
                ? trimmed
                : trimmed.substring(0, MAX_CHECKOUT_ERROR_LENGTH);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
