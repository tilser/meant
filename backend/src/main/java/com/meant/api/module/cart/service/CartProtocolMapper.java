package com.meant.api.module.cart.service;

import static com.meant.api.common.util.CollectionUtils.safeNonNullList;
import static com.meant.api.module.cart.service.CheckoutFulfillmentSupport.trimToNull;

import com.meant.api.module.cart.service.command.CompleteCheckoutCommand;
import com.meant.api.module.cart.service.dto.CartBuyerIdentityInput;
import com.meant.api.module.cart.service.dto.CartDeliveryAddressInput;
import com.meant.api.module.cart.service.dto.CartDeliveryAddressSelectionInput;
import com.meant.api.module.cart.service.dto.CartDeliveryOptionSelectionInput;
import com.meant.api.module.checkout.service.command.NativeCheckoutCompletionCommand;
import com.meant.api.module.user.service.dto.ResolvedSelectedOffer;
import com.meant.api.plugin.cart.common.dto.CartAddItem;
import com.meant.api.plugin.cart.common.dto.CartBuyer;
import com.meant.api.plugin.cart.common.dto.CartContext;
import com.meant.api.plugin.cart.common.dto.CartDeliveryAddress;
import com.meant.api.plugin.cart.common.dto.CartDeliveryAddressSelection;
import com.meant.api.plugin.cart.common.dto.CartDeliveryOptionSelection;
import com.meant.api.plugin.checkout.common.dto.CheckoutContext;
import com.meant.api.plugin.checkout.complete.dto.CheckoutSignals;
import java.util.List;
import java.util.Locale;

/** Maps module-owned cart inputs to the strongly typed UCP protocol payloads. */
final class CartProtocolMapper {

    private CartProtocolMapper() {
    }

    static CartBuyer cartBuyer(CartBuyerIdentityInput buyer) {
        return buyer == null ? null : new CartBuyer(
                trimToNull(buyer.firstName()),
                trimToNull(buyer.lastName()),
                trimToNull(buyer.email()),
                trimToNull(buyer.phoneNumber())
        );
    }

    static CartContext cartContext(CartContext context, CartBuyerIdentityInput buyer) {
        String buyerCountry = buyer == null ? null : trimToNull(buyer.countryCode());
        return buyerCountry == null ? context : context.merge(new CartContext(buyerCountry.toUpperCase(Locale.ROOT)));
    }

    static List<CartDeliveryAddressSelection> cartDeliveryAddresses(
            List<CartDeliveryAddressSelectionInput> inputs
    ) {
        return safeNonNullList(inputs).stream()
                .map(input -> new CartDeliveryAddressSelection(
                        trimToNull(input.methodId()), input.selected(), cartDeliveryAddress(input)))
                .toList();
    }

    static List<CartDeliveryOptionSelection> cartDeliveryOptions(
            List<CartDeliveryOptionSelectionInput> inputs
    ) {
        return safeNonNullList(inputs).stream()
                .map(input -> new CartDeliveryOptionSelection(
                        trimToNull(input.methodId()),
                        trimToNull(input.groupId()),
                        trimToNull(input.selectedOptionId())
                ))
                .toList();
    }

    static CheckoutContext checkoutContext(CartContext context) {
        if (context == null || context.empty()) {
            return null;
        }
        return new CheckoutContext(
                trimToNull(context.addressCountry()),
                trimToNull(context.addressRegion()),
                trimToNull(context.postalCode()),
                trimToNull(context.intent()),
                trimToNull(context.language()),
                trimToNull(context.currency()),
                context.eligibility(),
                context.extensions()
        );
    }

    static NativeCheckoutCompletionCommand nativeCompletionCommand(CompleteCheckoutCommand command) {
        return new NativeCheckoutCompletionCommand(
                command.cartId(),
                command.userId(),
                command.buyerConsentId(),
                command.checkoutId(),
                command.paymentInstruments(),
                command.idempotencyKey(),
                command.ap2SecurityLock(),
                ap2MandateInput(command.ap2Mandate()),
                checkoutSignals(command.signals()),
                command.buyerIp()
        );
    }

    static CartAddItem cartAddItem(ResolvedSelectedOffer offer, Integer quantity) {
        var identity = offer.identity();
        var reference = offer.rehydratedReference();
        return new CartAddItem(
                null,
                reference.externalVariantReference() == null ? null : reference.externalVariantReference().value(),
                identity.selectedOptions().stream()
                        .map(option -> new CartAddItem.SelectedOption(option.group(), option.name(), option.value()))
                        .toList(),
                identity.components().stream()
                        .map(component -> new CartAddItem.Component(
                                component.externalProductIdentity().value(),
                                component.externalVariantIdentity() == null
                                        ? null : component.externalVariantIdentity().value(),
                                component.quantity(),
                                component.selectedOptions().stream()
                                        .map(option -> new CartAddItem.SelectedOption(
                                                option.group(), option.name(), option.value()))
                                        .toList()
                        ))
                        .toList(),
                identity.sellingPlanIdentity() == null ? null : new CartAddItem.SellingPlan(
                        identity.sellingPlanIdentity().groupReference() == null
                                ? null : identity.sellingPlanIdentity().groupReference().value(),
                        identity.sellingPlanIdentity().planReference() == null
                                ? null : identity.sellingPlanIdentity().planReference().value(),
                        identity.sellingPlanIdentity().options().stream()
                                .map(option -> new CartAddItem.Option(option.name(), option.value()))
                                .toList()),
                quantity
        );
    }

    static List<String> normalizeCodes(List<String> codes) {
        if (codes == null) {
            return null;
        }
        return codes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private static CartDeliveryAddress cartDeliveryAddress(CartDeliveryAddressSelectionInput input) {
        CartDeliveryAddressInput address = input.address();
        return new CartDeliveryAddress(
                trimToNull(input.id()),
                address == null ? null : trimToNull(address.firstName()),
                address == null ? null : trimToNull(address.lastName()),
                address == null ? null : trimToNull(address.phoneNumber()),
                address == null ? null : trimToNull(address.streetAddress()),
                address == null ? null : trimToNull(address.extendedAddress()),
                address == null ? null : trimToNull(address.addressLocality()),
                address == null ? null : trimToNull(address.addressRegion()),
                address == null ? null : trimToNull(address.postalCode()),
                address == null ? null : trimToNull(address.addressCountry())
        );
    }

    private static NativeCheckoutCompletionCommand.Ap2MandateInput ap2MandateInput(
            CompleteCheckoutCommand.Ap2MandateCommand command
    ) {
        if (command == null) {
            return null;
        }
        return new NativeCheckoutCompletionCommand.Ap2MandateInput(
                command.merchantPublicJwk(),
                command.expectedMerchantAuthorizationKid(),
                command.merchantAuthorizationIssuer(),
                command.agentIssuer(),
                command.audience(),
                command.nonce(),
                command.expiresAt(),
                command.merchantAuthorizationJws()
        );
    }

    private static CheckoutSignals checkoutSignals(CompleteCheckoutCommand.CheckoutSignalsCommand command) {
        if (command == null) {
            return null;
        }
        return new CheckoutSignals(command.checkoutSurface(), command.userAgent());
    }
}
