package com.meant.api.plugin.cart.common.service;

import static com.meant.api.common.util.CollectionUtils.safeNonNullList;

import com.meant.api.module.cart.exception.CartException;
import com.meant.api.module.merchant.exception.MerchantMcpToolException;
import com.meant.api.module.merchant.service.MerchantMcpToolClient;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.module.merchant.service.dto.MerchantMcpToolCallResult;
import com.meant.api.plugin.cart.cancel.CancelCartCapability;
import com.meant.api.plugin.cart.cancel.dto.CancelCartRequest;
import com.meant.api.plugin.cart.cancel.dto.CancelCartResponse;
import com.meant.api.plugin.cart.common.dto.CartAddItem;
import com.meant.api.plugin.cart.common.dto.CartUpdateItem;
import com.meant.api.plugin.cart.common.dto.UcpCartResponse;
import com.meant.api.plugin.cart.common.dto.UcpCartToolResult;
import com.meant.api.plugin.cart.create.CreateCartCapability;
import com.meant.api.plugin.cart.create.dto.CreateCartRequest;
import com.meant.api.plugin.cart.get.GetCartCapability;
import com.meant.api.plugin.cart.get.dto.GetCartRequest;
import com.meant.api.plugin.cart.update.UpdateCartCapability;
import com.meant.api.plugin.cart.update.dto.UpdateCartRequest;
import com.meant.api.plugin.spi.UcpCapability;
import com.meant.api.plugin.spi.UcpToolResponse;
import com.meant.api.plugin.support.UcpSession;
import com.meant.api.plugin.transport.registry.CapabilityRegistry;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class MerchantCartPluginDispatchService {

    private static final int MAX_CART_ERROR_LENGTH = 180;

    private final MerchantMcpToolClient merchantMcpToolClient;
    private final CapabilityRegistry capabilityRegistry;
    private final ObjectMapper objectMapper;

    public UcpCartToolResult createCart(
            MerchantCartProvider provider,
            CreateCartRequest request,
            UcpSession session
    ) {
        CreateCartCapability capability = capability(CreateCartCapability.TOOL_NAME, CreateCartCapability.class);
        MerchantMcpToolCallResult result = callCreateCart(provider, request, session, capability);
        UcpCartResponse response = parseCartResponse(capability, result, "create cart");
        rejectCartProblems(null, request.discountCodes(), request.giftCardCodes(), response);
        updateSession(session, result, response);
        return cartResult(result, response);
    }

    public UcpCartToolResult getCart(
            MerchantCartProvider provider,
            GetCartRequest request,
            UcpSession session
    ) {
        GetCartCapability capability = capability(GetCartCapability.TOOL_NAME, GetCartCapability.class);
        MerchantMcpToolCallResult result = callWithLegacyCartIdFallback(
                provider,
                GetCartCapability.TOOL_NAME,
                capability.buildArguments(request, session.activeCapabilities()),
                "cart_id",
                request.cartId()
        );
        UcpCartResponse response = parseCartResponse(capability, result, "get cart");
        rejectCartProblems(request.cartId(), List.of(), List.of(), response);
        updateSession(session, result, response);
        return cartResult(result, response);
    }

    public UcpCartToolResult updateCart(
            MerchantCartProvider provider,
            UpdateCartRequest request,
            UcpSession session
    ) {
        UpdateCartCapability capability = capability(UpdateCartCapability.TOOL_NAME, UpdateCartCapability.class);
        MerchantMcpToolCallResult result = callUpdateCart(provider, request, session, capability);
        UcpCartResponse response = parseCartResponse(capability, result, "update cart");
        rejectCartProblems(request.cartId(), request.discountCodes(), request.giftCardCodes(), response);
        updateSession(session, result, response);
        return cartResult(result, response);
    }

    public CancelCartResponse cancelCart(
            MerchantCartProvider provider,
            CancelCartRequest request,
            UcpSession session
    ) {
        CancelCartCapability capability = capability(CancelCartCapability.TOOL_NAME, CancelCartCapability.class);
        MerchantMcpToolCallResult result = callWithLegacyCartIdFallback(
                provider,
                CancelCartCapability.TOOL_NAME,
                capability.buildArguments(request, session.activeCapabilities()),
                "cart_id",
                request.cartId()
        );
        CancelCartResponse response = capability.parseResponse(toolResponse(result));
        rejectCancelProblems(request.cartId(), response);
        session.acceptNegotiatedCapabilities(result.negotiatedCapabilities());
        session.clearCartState();
        return response;
    }

    private MerchantMcpToolCallResult callCreateCart(
            MerchantCartProvider provider,
            CreateCartRequest request,
            UcpSession session,
            CreateCartCapability capability
    ) {
        try {
            return merchantMcpToolClient.callTool(
                    provider,
                    CreateCartCapability.TOOL_NAME,
                    capability.buildArguments(request, session.activeCapabilities())
            );
        } catch (MerchantMcpToolException primaryException) {
            try {
                return merchantMcpToolClient.callTool(
                        provider,
                        CreateCartCapability.TOOL_NAME,
                        legacyCreateCartArguments(request)
                );
            } catch (MerchantMcpToolException legacyCreateException) {
                legacyCreateException.addSuppressed(primaryException);
                try {
                    return merchantMcpToolClient.callTool(
                            provider,
                            UpdateCartCapability.TOOL_NAME,
                            legacyCreateWithUpdateCartArguments(request)
                    );
                } catch (MerchantMcpToolException legacyUpdateException) {
                    legacyUpdateException.addSuppressed(legacyCreateException);
                    throw legacyUpdateException;
                }
            }
        }
    }

    private MerchantMcpToolCallResult callUpdateCart(
            MerchantCartProvider provider,
            UpdateCartRequest request,
            UcpSession session,
            UpdateCartCapability capability
    ) {
        try {
            return merchantMcpToolClient.callTool(
                    provider,
                    UpdateCartCapability.TOOL_NAME,
                    capability.buildArguments(request, session.activeCapabilities())
            );
        } catch (MerchantMcpToolException exception) {
            return callLegacyAfterFailure(
                    provider,
                    UpdateCartCapability.TOOL_NAME,
                    legacyUpdateCartArguments(request),
                    exception
            );
        }
    }

    private MerchantMcpToolCallResult callWithLegacyCartIdFallback(
            MerchantCartProvider provider,
            String toolName,
            Object primaryArguments,
            String legacyIdKey,
            String cartId
    ) {
        try {
            return merchantMcpToolClient.callTool(provider, toolName, primaryArguments);
        } catch (MerchantMcpToolException exception) {
            Map<String, Object> legacyArguments = new LinkedHashMap<>();
            put(legacyArguments, legacyIdKey, cartId);
            return callLegacyAfterFailure(provider, toolName, legacyArguments, exception);
        }
    }

    private MerchantMcpToolCallResult callLegacyAfterFailure(
            MerchantCartProvider provider,
            String toolName,
            Object legacyArguments,
            MerchantMcpToolException primaryException
    ) {
        try {
            return merchantMcpToolClient.callTool(provider, toolName, legacyArguments);
        } catch (MerchantMcpToolException legacyException) {
            legacyException.addSuppressed(primaryException);
            throw legacyException;
        }
    }

    private Map<String, Object> legacyCreateCartArguments(CreateCartRequest request) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        put(arguments, "add_items", request.addItems());
        put(arguments, "buyer_identity", request.buyerIdentity());
        put(arguments, "delivery_addresses_to_add", legacyDeliveryAddresses(request.deliveryAddressesToAdd()));
        put(arguments, "delivery_addresses_to_replace", legacyDeliveryAddresses(request.deliveryAddressesToReplace()));
        put(arguments, "selected_delivery_options", legacySelectedDeliveryOptions(request.selectedDeliveryOptions()));
        put(arguments, "discount_codes", request.discountCodes());
        put(arguments, "gift_card_codes", request.giftCardCodes());
        put(arguments, "note", request.note());
        return arguments;
    }

    private Map<String, Object> legacyCreateWithUpdateCartArguments(CreateCartRequest request) {
        return legacyUpdateCartArguments(
                null,
                request.addItems(),
                List.of(),
                List.of(),
                request.buyerIdentity(),
                request.deliveryAddressesToAdd(),
                request.deliveryAddressesToReplace(),
                request.selectedDeliveryOptions(),
                request.discountCodes(),
                request.giftCardCodes(),
                request.note()
        );
    }

    private Map<String, Object> legacyUpdateCartArguments(UpdateCartRequest request) {
        return legacyUpdateCartArguments(
                request.cartId(),
                request.addItems(),
                request.updateItems(),
                request.removeLineIds(),
                request.buyerIdentity(),
                request.deliveryAddressesToAdd(),
                request.deliveryAddressesToReplace(),
                request.selectedDeliveryOptions(),
                request.discountCodes(),
                request.giftCardCodes(),
                request.note()
        );
    }

    private Map<String, Object> legacyUpdateCartArguments(
            String cartId,
            List<CartAddItem> addItems,
            List<CartUpdateItem> updateItems,
            List<String> removeLineIds,
            Map<String, Object> buyerIdentity,
            List<Map<String, Object>> deliveryAddressesToAdd,
            List<Map<String, Object>> deliveryAddressesToReplace,
            List<Map<String, Object>> selectedDeliveryOptions,
            List<String> discountCodes,
            List<String> giftCardCodes,
            String note
    ) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        put(arguments, "cart_id", cartId);
        put(arguments, "add_items", addItems);
        put(arguments, "update_items", updateItems);
        put(arguments, "remove_line_ids", removeLineIds);
        put(arguments, "buyer_identity", buyerIdentity);
        put(arguments, "delivery_addresses_to_add", legacyDeliveryAddresses(deliveryAddressesToAdd));
        put(arguments, "delivery_addresses_to_replace", legacyDeliveryAddresses(deliveryAddressesToReplace));
        put(arguments, "selected_delivery_options", legacySelectedDeliveryOptions(selectedDeliveryOptions));
        put(arguments, "discount_codes", discountCodes);
        put(arguments, "gift_card_codes", giftCardCodes);
        put(arguments, "note", note);
        return arguments;
    }

    private List<Map<String, Object>> legacyDeliveryAddresses(List<Map<String, Object>> deliveryAddresses) {
        return safeNonNullList(deliveryAddresses).stream()
                .map(this::legacyDeliveryAddress)
                .filter(map -> !map.isEmpty())
                .toList();
    }

    private Map<String, Object> legacyDeliveryAddress(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> existingAddress = mapValue(source.get("delivery_address"));
        if (!existingAddress.isEmpty()) {
            return source;
        }

        Map<String, Object> address = new LinkedHashMap<>();
        put(address, "first_name", firstValue(source, "first_name", "firstName"));
        put(address, "last_name", firstValue(source, "last_name", "lastName"));
        put(address, "phone", firstValue(source, "phone", "phone_number"));
        put(address, "address1", firstValue(source, "address1", "street_address"));
        put(address, "address2", firstValue(source, "address2", "extended_address"));
        put(address, "city", firstValue(source, "city", "address_locality"));
        put(address, "province_code", firstValue(source, "province_code", "province", "address_region"));
        put(address, "zip", firstValue(source, "zip", "postal_code"));
        put(address, "country_code", firstValue(source, "country_code", "country", "address_country"));

        Map<String, Object> result = new LinkedHashMap<>();
        put(result, "selected", firstValue(source, "selected"));
        put(result, "delivery_address", address);
        return result;
    }

    private List<Map<String, Object>> legacySelectedDeliveryOptions(List<Map<String, Object>> selectedDeliveryOptions) {
        return safeNonNullList(selectedDeliveryOptions).stream()
                .map(this::legacySelectedDeliveryOption)
                .filter(map -> !map.isEmpty())
                .toList();
    }

    private Map<String, Object> legacySelectedDeliveryOption(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        put(result, "group_id", firstValue(source, "group_id", "delivery_group_id", "id"));
        put(result, "option_handle", firstValue(source, "option_handle", "delivery_option_handle", "selected_option_id"));
        return result;
    }

    private Object firstValue(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, mapValue) -> {
            if (key != null) {
                result.put(key.toString(), mapValue);
            }
        });
        return result;
    }

    private void put(Map<String, Object> destination, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String text && text.isBlank()) {
            return;
        }
        if (value instanceof Collection<?> values && values.isEmpty()) {
            return;
        }
        if (value instanceof Map<?, ?> values && values.isEmpty()) {
            return;
        }
        destination.put(key, value);
    }

    private UcpCartResponse parseCartResponse(
            UcpCapability<?, UcpCartResponse> capability,
            MerchantMcpToolCallResult result,
            String operation
    ) {
        UcpCartResponse response = capability.parseResponse(toolResponse(result));
        if (response == null) {
            throw CartException.upstream("UCP " + operation + " response was empty");
        }
        return response;
    }

    private void rejectCartProblems(
            String cartId,
            Collection<String> discountCodes,
            Collection<String> giftCardCodes,
            UcpCartResponse response
    ) {
        UcpCartResponse.CartError error = firstError(response.errors());
        if (error != null) {
            if (error.isNotFound()) {
                throw CartException.notFound("Cart not found: " + cartId);
            }
            throw CartException.rejected(safeCartErrorMessage(error.message()));
        }

        UcpCartResponse.CartMessage message = firstErrorMessage(response);
        if (message != null) {
            if (message.isNotFound()) {
                throw CartException.notFound("Cart not found: " + cartId);
            }
            throw CartException.rejected(safeCartErrorMessage(message.message()));
        }

        if (response.cart() == null) {
            throw CartException.upstream("UCP cart response did not contain cart");
        }
        rejectInapplicableCodes("Discount code", discountCodes, response.cart().discountCodes(), false);
        rejectInapplicableCodes("Gift card code", giftCardCodes, response.cart().giftCardCodes(), true);
    }

    private void rejectCancelProblems(String cartId, CancelCartResponse response) {
        if (response == null) {
            throw CartException.upstream("UCP cancel_cart response was empty");
        }
        UcpCartResponse.CartError error = firstError(response.errors());
        if (error != null) {
            if (error.isNotFound()) {
                throw CartException.notFound("Cart not found: " + cartId);
            }
            throw CartException.rejected(safeCartErrorMessage(error.message()));
        }
        UcpCartResponse.CartMessage message = safeNonNullList(response.messages()).stream()
                .filter(UcpCartResponse.CartMessage::isError)
                .findFirst()
                .orElse(null);
        if (message != null) {
            if (message.isNotFound()) {
                throw CartException.notFound("Cart not found: " + cartId);
            }
            throw CartException.rejected(safeCartErrorMessage(message.message()));
        }
    }

    private UcpCartResponse.CartError firstError(List<UcpCartResponse.CartError> errors) {
        if (errors == null || errors.isEmpty()) {
            return null;
        }
        return errors.stream()
                .filter(error -> error != null && (error.isNotFound() || hasText(error.message())))
                .findFirst()
                .orElse(null);
    }

    private UcpCartResponse.CartMessage firstErrorMessage(UcpCartResponse response) {
        return Stream.concat(
                        safeNonNullList(response.messages()).stream(),
                        response.cart() == null
                                ? Stream.empty()
                                : safeNonNullList(response.cart().messages()).stream()
                )
                .filter(UcpCartResponse.CartMessage::isError)
                .findFirst()
                .orElse(null);
    }

    private void rejectInapplicableCodes(
            String label,
            Collection<String> submittedCodes,
            List<UcpCartResponse.AppliedCode> responseCodes,
            boolean matchSuffix
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
                .map(UcpCartResponse.AppliedCode::code)
                .filter(responseCode -> responseCode != null && !responseCode.isBlank())
                .filter(responseCode -> matchesSubmittedCode(responseCode, normalizedSubmittedCodes, matchSuffix))
                .toList();
        if (!rejectedCodes.isEmpty()) {
            throw CartException.rejected(label + " " + rejectedCodes.getFirst() + " was not accepted by the merchant.");
        }
    }

    private void updateSession(
            UcpSession session,
            MerchantMcpToolCallResult result,
            UcpCartResponse response
    ) {
        session.acceptNegotiatedCapabilities(result.negotiatedCapabilities());
        UcpCartResponse.Cart cart = response.cart();
        session.updateCartState(cart.id(), cart.expiresAt(), handoffUrl(cart));
    }

    private UcpCartToolResult cartResult(MerchantMcpToolCallResult result, UcpCartResponse response) {
        return new UcpCartToolResult(result.endpoint(), rawResponse(result, response), response);
    }

    private String rawResponse(MerchantMcpToolCallResult result, UcpCartResponse response) {
        if (result.contentText() != null && !result.contentText().isBlank()) {
            return result.contentText();
        }
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JacksonException exception) {
            throw CartException.upstream("Could not serialize UCP cart response", exception);
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

    private String handoffUrl(UcpCartResponse.Cart cart) {
        if (cart.continueUrl() != null && !cart.continueUrl().isBlank()) {
            return cart.continueUrl();
        }
        return cart.checkoutUrl();
    }

    private boolean matchesSubmittedCode(String responseCode, Set<String> normalizedSubmittedCodes, boolean matchSuffix) {
        String normalizedResponseCode = responseCode.toLowerCase(Locale.ROOT);
        if (normalizedSubmittedCodes.contains(normalizedResponseCode)) {
            return true;
        }
        return matchSuffix && normalizedSubmittedCodes.stream()
                .anyMatch(submittedCode -> submittedCode.endsWith(normalizedResponseCode));
    }

    private String safeCartErrorMessage(String message) {
        String trimmed = hasText(message) ? message.trim() : "The merchant rejected this cart operation.";
        return trimmed.length() <= MAX_CART_ERROR_LENGTH
                ? trimmed
                : trimmed.substring(0, MAX_CART_ERROR_LENGTH);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
