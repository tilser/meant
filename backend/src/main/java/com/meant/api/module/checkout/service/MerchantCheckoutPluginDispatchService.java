package com.meant.api.module.checkout.service;

import static com.meant.api.common.util.CollectionUtils.safeNonNullList;

import com.meant.api.module.cart.exception.CartException;
import com.meant.api.module.cart.service.dto.CartRoutingTarget;
import com.meant.api.module.merchant.constant.CommerceOperation;
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
import com.meant.api.module.checkout.service.port.CheckoutToolTransport;
import com.meant.api.module.checkout.service.dto.CheckoutToolCallContext;
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
@RequiredArgsConstructor(onConstructor_ = @org.springframework.beans.factory.annotation.Autowired)
public class MerchantCheckoutPluginDispatchService {

    private static final int MAX_CHECKOUT_ERROR_LENGTH = 180;

    private final MerchantMcpToolClient merchantMcpToolClient;
    private final CapabilityRegistry capabilityRegistry;
    private final ObjectMapper objectMapper;
    private final List<CheckoutToolTransport> checkoutToolTransports;

    public UcpCheckoutToolResult createCheckout(
            MerchantCartProvider provider,
            CreateCheckoutRequest request,
            UcpSession session
    ) {
        provider = provider.forOperation(CommerceOperation.CHECKOUT_SESSION);
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

    public UcpCheckoutToolResult createCheckout(
            CartRoutingTarget target, CreateCheckoutRequest request, UcpSession session) {
        return createCheckout(target, request, session, CheckoutToolCallContext.standard());
    }

    public UcpCheckoutToolResult createCheckout(
            CartRoutingTarget target, CreateCheckoutRequest request, UcpSession session,
            CheckoutToolCallContext context) {
        if (!hasProviderTransport(target)) {
            return createCheckout(target.merchantProvider(), request, session);
        }
        CreateCheckoutCapability capability = capability(CreateCheckoutCapability.TOOL_NAME, CreateCheckoutCapability.class);
        MerchantMcpToolCallResult result = call(target, CreateCheckoutCapability.TOOL_NAME,
                capability.buildArguments(request, session.activeCapabilities()), context);
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
        provider = provider.forOperation(CommerceOperation.CHECKOUT_SESSION);
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

    public UcpCheckoutToolResult getCheckout(
            CartRoutingTarget target, GetCheckoutRequest request, UcpSession session) {
        return getCheckout(target, request, session, CheckoutToolCallContext.standard());
    }

    public UcpCheckoutToolResult getCheckout(
            CartRoutingTarget target, GetCheckoutRequest request, UcpSession session,
            CheckoutToolCallContext context) {
        if (!hasProviderTransport(target)) {
            return getCheckout(target.merchantProvider(), request, session);
        }
        GetCheckoutCapability capability = capability(GetCheckoutCapability.TOOL_NAME, GetCheckoutCapability.class);
        MerchantMcpToolCallResult result = call(target, GetCheckoutCapability.TOOL_NAME,
                capability.buildArguments(request, session.activeCapabilities()), context);
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
        provider = provider.forOperation(CommerceOperation.CHECKOUT_SESSION);
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

    public UcpCheckoutToolResult updateCheckout(
            CartRoutingTarget target, UpdateCheckoutRequest request, UcpSession session) {
        return updateCheckout(target, request, session, CheckoutToolCallContext.standard());
    }

    public UcpCheckoutToolResult updateCheckout(
            CartRoutingTarget target, UpdateCheckoutRequest request, UcpSession session,
            CheckoutToolCallContext context) {
        if (!hasProviderTransport(target)) {
            return updateCheckout(target.merchantProvider(), request, session);
        }
        UpdateCheckoutCapability capability = capability(UpdateCheckoutCapability.TOOL_NAME, UpdateCheckoutCapability.class);
        MerchantMcpToolCallResult result = call(target, UpdateCheckoutCapability.TOOL_NAME,
                capability.buildArguments(request, session.activeCapabilities()), context);
        UcpCheckoutResponse response = parseCheckoutResponse(capability, result, "update checkout");
        rejectCheckoutProblems("Checkout not found: " + request.checkoutId(), response);
        updateSession(session, result, response);
        return checkoutResult(result, response);
    }

    public UcpCheckoutToolResult cancelCheckout(
            CartRoutingTarget target, CancelCheckoutRequest request, UcpSession session,
            CheckoutToolCallContext context) {
        if (!hasProviderTransport(target)) {
            return cancelCheckout(target.merchantProvider(), request, session,
                    context.idempotencyKey() == null ? Map.of()
                            : Map.of("Idempotency-Key", context.idempotencyKey().toString()));
        }
        CancelCheckoutCapability capability = capability(CancelCheckoutCapability.TOOL_NAME, CancelCheckoutCapability.class);
        MerchantMcpToolCallResult result = call(target, CancelCheckoutCapability.TOOL_NAME,
                capability.buildArguments(request, session.activeCapabilities()), context);
        UcpCheckoutResponse response = parseCheckoutResponse(capability, result, "cancel checkout");
        updateSessionIfCheckoutPresent(session, result, response);
        return checkoutResult(result, response);
    }

    private MerchantMcpToolCallResult call(
            CartRoutingTarget target, String toolName, Object arguments, CheckoutToolCallContext context) {
        List<CheckoutToolTransport> matching = checkoutToolTransports.stream()
                .filter(transport -> transport.supports(target)).toList();
        if (matching.size() > 1) {
            throw CartException.binding(CartException.BindingFailure.AMBIGUOUS_ROUTING,
                    "Checkout provider transport is ambiguous");
        }
        if (matching.size() == 1) {
            return matching.getFirst().call(target, toolName, arguments, context);
        }
        return merchantMcpToolClient.callToolReturningJsonToolErrors(
                target.merchantProvider().forOperation(CommerceOperation.CHECKOUT_SESSION), toolName, arguments,
                context.idempotencyKey() == null ? Map.of()
                        : Map.of("Idempotency-Key", context.idempotencyKey().toString()));
    }

    private boolean hasProviderTransport(CartRoutingTarget target) {
        long count = checkoutToolTransports.stream().filter(transport -> transport.supports(target)).count();
        if (count > 1) {
            throw CartException.binding(CartException.BindingFailure.AMBIGUOUS_ROUTING,
                    "Checkout provider transport is ambiguous");
        }
        return count == 1;
    }

    public UcpCheckoutToolResult completeCheckout(
            MerchantCartProvider provider,
            CompleteCheckoutRequest request,
            UcpSession session,
            Map<String, String> signedHeaders
    ) {
        provider = provider.forOperation(CommerceOperation.DIRECT_CHECKOUT_COMPLETION);
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
        provider = provider.forOperation(CommerceOperation.DIRECT_CHECKOUT_COMPLETION);
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
                .filter(message -> message.isNotFound()
                        || (message.isError() && !message.isRecoverable() && !message.requiresBuyerAction()))
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
