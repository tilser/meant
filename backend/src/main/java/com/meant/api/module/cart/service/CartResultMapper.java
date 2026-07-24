package com.meant.api.module.cart.service;

import static com.meant.api.common.util.CollectionUtils.safeNonNullList;

import com.meant.api.module.cart.entity.Cart;
import com.meant.api.module.cart.service.dto.CartAppliedCodeResult;
import com.meant.api.module.cart.service.dto.CartDeliveryGroupResult;
import com.meant.api.module.cart.service.dto.CartDeliveryOptionResult;
import com.meant.api.module.cart.service.dto.CartLineResult;
import com.meant.api.module.cart.service.dto.CartMessageResult;
import com.meant.api.module.cart.service.dto.CartResult;
import com.meant.api.module.merchant.service.MerchantBuyerTextSanitizer;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.plugin.cart.common.dto.UcpCartResponse;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartResultMapper {

    private static final Pattern TRANSPORT_COORDINATE = Pattern.compile(
            "(?i)(?:https?://|\\b(?:[a-z0-9-]+\\.)+[a-z]{2,63}\\b|"
                    + "/(?:\\.well-known/ucp|api/ucp/mcp|mcp))"
    );

    private final ObjectMapper objectMapper;

    public CartResult from(Cart cart) {
        return from(cart, (MerchantCartProvider) null);
    }

    public CartResult from(Cart cart, MerchantCartProvider provider) {
        UcpCartResponse response = storedResponse(cart);
        return sanitizeBuyerText(cart, provider, CartResult.from(
                cart,
                deliveryGroups(response),
                messages(cart, response, provider),
                sanitize(
                        cart,
                        provider,
                        response == null ? cart.getInstructions() : response.instructions()
                )
        ));
    }

    public CartResult from(Cart cart, UcpCartResponse currentResponse) {
        return from(cart, currentResponse, null);
    }

    public CartResult from(
            Cart cart,
            UcpCartResponse currentResponse,
            MerchantCartProvider provider
    ) {
        return sanitizeBuyerText(cart, provider, CartResult.from(
                cart,
                currentResponse,
                deliveryGroups(currentResponse),
                messages(cart, currentResponse, provider),
                sanitize(
                        cart,
                        provider,
                        currentResponse == null ? cart.getInstructions() : currentResponse.instructions()
                )
        ));
    }

    boolean storedBuyerTextMayContainTransport(Cart cart) {
        UcpCartResponse response = storedResponse(cart);
        Stream<String> responseText = response == null
                ? Stream.empty()
                : Stream.concat(
                        Stream.of(response.instructions()),
                        Stream.concat(
                                safeNonNullList(response.messages()).stream(),
                                response.cart() == null
                                        ? Stream.empty()
                                        : safeNonNullList(response.cart().messages()).stream()
                        ).flatMap(message -> message == null
                                ? Stream.empty()
                                : Stream.of(message.message(), message.target()))
                );
        Stream<String> appliedCodeText = cart.getAppliedCodes().stream()
                .flatMap(code -> Stream.of(code.getLabel()));
        Stream<String> deliveryText = response == null || response.cart() == null
                ? Stream.empty()
                : safeNonNullList(response.cart().deliveryGroups()).stream()
                        .filter(group -> group != null)
                        .flatMap(group -> Stream.concat(
                                safeNonNullList(group.deliveryOptions()).stream(),
                                Stream.ofNullable(group.selectedDeliveryOption())
                        ))
                        .filter(option -> option != null)
                        .flatMap(option -> Stream.of(
                                option.title(),
                                option.description(),
                                option.code(),
                                option.deliveryMethodType(),
                                option.deliveryEstimate(),
                                option.estimatedDeliveryTime()
                        ));
        Stream<String> lineText = response == null || response.cart() == null
                ? Stream.empty()
                : safeNonNullList(response.cart().lines()).stream()
                        .filter(line -> line != null)
                        .flatMap(line -> {
                            UcpCartResponse.Merchandise merchandise = line.merchandise();
                            UcpCartResponse.Product product =
                                    merchandise == null ? null : merchandise.product();
                            return Stream.of(
                                    product == null ? null : product.title(),
                                    merchandise == null ? null : merchandise.title()
                            );
                        });
        return Stream.of(
                        Stream.of(cart.getInstructions()),
                        responseText,
                        appliedCodeText,
                        deliveryText,
                        lineText
                )
                .flatMap(stream -> stream)
                .filter(value -> value != null && !value.isBlank())
                .anyMatch(value -> TRANSPORT_COORDINATE.matcher(value).find());
    }

    private UcpCartResponse storedResponse(Cart cart) {
        String rawCartResponse = cart.getRawCartResponse();
        if (rawCartResponse == null || rawCartResponse.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(rawCartResponse, UcpCartResponse.class);
        } catch (JacksonException exception) {
            log.warn("Could not parse stored cart response");
            return null;
        }
    }

    private List<CartDeliveryGroupResult> deliveryGroups(UcpCartResponse response) {
        if (response == null || response.cart() == null) {
            return List.of();
        }
        return safeNonNullList(response.cart().deliveryGroups()).stream()
                .map(CartDeliveryGroupResult::from)
                .filter(group -> group != null)
                .toList();
    }

    private List<CartMessageResult> messages(
            Cart cart,
            UcpCartResponse response,
            MerchantCartProvider provider
    ) {
        if (response == null) {
            return List.of();
        }
        return java.util.stream.Stream.concat(
                        safeNonNullList(response.messages()).stream(),
                        response.cart() == null
                                ? java.util.stream.Stream.empty()
                                : safeNonNullList(response.cart().messages()).stream()
                )
                .map(CartMessageResult::from)
                .filter(message -> message != null)
                .map(message -> new CartMessageResult(
                        message.code(),
                        message.severity(),
                        message.type(),
                        sanitize(cart, provider, message.message()),
                        sanitize(cart, provider, message.target())
                ))
                .toList();
    }

    private String sanitize(Cart cart, MerchantCartProvider provider, String value) {
        String providerSafe = MerchantBuyerTextSanitizer.sanitize(value, provider);
        return MerchantBuyerTextSanitizer.sanitize(
                providerSafe,
                cart.getMerchantDomain(),
                cart.getRoutingDomain(),
                cart.getEndpoint()
        );
    }

    private CartResult sanitizeBuyerText(
            Cart cart,
            MerchantCartProvider provider,
            CartResult result
    ) {
        return new CartResult(
                result.cartId(),
                result.merchantId(),
                result.merchantDomain(),
                result.provider(),
                result.merchantIntegrationId(),
                result.externalMerchantId(),
                result.routingScopeKey(),
                result.endpoint(),
                result.remoteCartId(),
                BuyerSafeCheckoutUrl.firstSafe(
                        cart,
                        provider,
                        result.checkoutUrl(),
                        cart.getCheckoutUrl()
                ),
                BuyerSafeCheckoutUrl.firstSafe(
                        cart,
                        provider,
                        result.continueUrl(),
                        cart.getContinueUrl()
                ),
                result.instructions(),
                result.totalQuantity(),
                result.totalAmount(),
                result.subtotalAmount(),
                result.currency(),
                result.active(),
                result.remoteCreatedAt(),
                result.remoteUpdatedAt(),
                result.expiresAt(),
                result.createdAt(),
                result.updatedAt(),
                result.refreshedAt(),
                result.appliedCodes().stream()
                        .map(code -> sanitizeAppliedCode(cart, provider, code))
                        .toList(),
                result.lines().stream()
                        .map(line -> sanitizeLine(cart, provider, line))
                        .toList(),
                result.deliveryGroups().stream()
                        .map(group -> sanitizeDeliveryGroup(cart, provider, group))
                        .toList(),
                result.messages()
        );
    }

    private CartAppliedCodeResult sanitizeAppliedCode(
            Cart cart,
            MerchantCartProvider provider,
            CartAppliedCodeResult code
    ) {
        return new CartAppliedCodeResult(
                code.type(),
                code.code(),
                sanitize(cart, provider, code.label()),
                code.applicable(),
                code.amount(),
                code.currency()
        );
    }

    private CartLineResult sanitizeLine(
            Cart cart,
            MerchantCartProvider provider,
            CartLineResult line
    ) {
        return new CartLineResult(
                line.cartLineId(),
                line.remoteCartLineId(),
                line.productId(),
                sanitize(cart, provider, line.productTitle()),
                sanitize(cart, provider, line.productBrand()),
                line.productVariantId(),
                sanitize(cart, provider, line.variantTitle()),
                line.quantity(),
                line.totalAmount(),
                line.subtotalAmount(),
                line.currency(),
                line.offerKey(),
                line.canonicalProductKey(),
                sanitize(cart, provider, line.selectedOptionsJson()),
                sanitize(cart, provider, line.componentsJson()),
                sanitize(cart, provider, line.sellingPlanJson()),
                line.provider(),
                line.merchantIntegrationId(),
                line.externalMerchantId(),
                line.createdAt(),
                line.updatedAt()
        );
    }

    private CartDeliveryGroupResult sanitizeDeliveryGroup(
            Cart cart,
            MerchantCartProvider provider,
            CartDeliveryGroupResult group
    ) {
        return new CartDeliveryGroupResult(
                group.id(),
                group.handle(),
                group.deliveryOptions().stream()
                        .map(option -> sanitizeDeliveryOption(cart, provider, option))
                        .toList(),
                group.selectedDeliveryOption() == null
                        ? null
                        : sanitizeDeliveryOption(cart, provider, group.selectedDeliveryOption())
        );
    }

    private CartDeliveryOptionResult sanitizeDeliveryOption(
            Cart cart,
            MerchantCartProvider provider,
            CartDeliveryOptionResult option
    ) {
        return new CartDeliveryOptionResult(
                option.handle(),
                sanitize(cart, provider, option.title()),
                sanitize(cart, provider, option.description()),
                sanitize(cart, provider, option.code()),
                option.cost(),
                sanitize(cart, provider, option.deliveryMethodType()),
                sanitize(cart, provider, option.deliveryEstimate()),
                sanitize(cart, provider, option.estimatedDeliveryTime()),
                option.estimatedDeliveryAt(),
                option.selected()
        );
    }
}
