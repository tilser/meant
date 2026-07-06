package com.meant.api.module.cart.controller.mapper;

import static com.meant.api.common.util.CollectionUtils.safeList;

import com.meant.api.module.cart.controller.request.CancelCheckoutRequest;
import com.meant.api.module.cart.controller.request.CartCreateRequest;
import com.meant.api.module.cart.controller.request.CartUpdateRequest;
import com.meant.api.module.cart.controller.request.CompleteCheckoutRequest;
import com.meant.api.module.cart.controller.request.CreateCheckoutConsentRequest;
import com.meant.api.module.cart.service.command.CancelCheckoutCommand;
import com.meant.api.module.cart.service.command.CompleteCheckoutCommand;
import com.meant.api.module.cart.service.command.CreateCartCommand;
import com.meant.api.module.cart.service.command.CreateCheckoutConsentCommand;
import com.meant.api.module.cart.service.command.UpdateCartCommand;
import com.meant.api.plugin.payment.common.dto.PaymentCredential;
import com.meant.api.plugin.payment.common.dto.PaymentInstrument;
import com.meant.api.plugin.payment.common.dto.PaymentScaLiability;
import com.meant.api.plugin.payment.common.dto.TokenPaymentCredentialDetails;
import com.meant.api.plugin.signing.JsonWebKey;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CartCommandMapper {

    public static CreateCartCommand toCommand(UUID userId, CartCreateRequest request) {
        return new CreateCartCommand(
                userId,
                request.merchantId(),
                request.merchantDomain(),
                safeList(request.addItems()).stream()
                        .map(item -> new CreateCartCommand.AddItem(
                                item.productVariantId(),
                                item.quantity()
                        ))
                        .toList(),
                request.buyerIdentity(),
                safeList(request.deliveryAddressesToAdd()),
                safeList(request.deliveryAddressesToReplace()),
                safeList(request.selectedDeliveryOptions()),
                request.discountCodes(),
                request.giftCardCodes(),
                request.note()
        );
    }

    public static UpdateCartCommand toCommand(UUID cartId, UUID userId, CartUpdateRequest request) {
        return new UpdateCartCommand(
                cartId,
                userId,
                safeList(request.addItems()).stream()
                        .map(item -> new UpdateCartCommand.AddItem(
                                item.productVariantId(),
                                item.quantity()
                        ))
                        .toList(),
                safeList(request.updateItems()).stream()
                        .map(item -> new UpdateCartCommand.UpdateItem(
                                item.cartLineId(),
                                item.remoteCartLineId(),
                                item.quantity()
                        ))
                        .toList(),
                safeList(request.removeCartLineIds()),
                safeList(request.removeRemoteCartLineIds()),
                request.buyerIdentity(),
                safeList(request.deliveryAddressesToAdd()),
                safeList(request.deliveryAddressesToReplace()),
                safeList(request.selectedDeliveryOptions()),
                request.discountCodes(),
                request.giftCardCodes(),
                request.note()
        );
    }

    public static CompleteCheckoutCommand toCommand(UUID cartId, UUID userId, CompleteCheckoutRequest request) {
        return new CompleteCheckoutCommand(
                cartId,
                userId,
                request.buyerConsentId(),
                request.checkoutId(),
                safeList(request.paymentInstruments()).stream()
                        .map(CartCommandMapper::paymentInstrument)
                        .toList(),
                request.idempotencyKey(),
                request.ap2SecurityLock(),
                ap2MandateCommand(request.ap2Mandate()),
                checkoutSignalsCommand(request.signals())
        );
    }

    public static CreateCheckoutConsentCommand toCommand(
            UUID cartId,
            UUID userId,
            CreateCheckoutConsentRequest request
    ) {
        return new CreateCheckoutConsentCommand(
                cartId,
                userId,
                request.checkoutId(),
                request.paymentInstrumentReference(),
                request.shippingMethod(),
                request.presentedTermsHash()
        );
    }

    public static CancelCheckoutCommand toCommand(UUID cartId, UUID userId, CancelCheckoutRequest request) {
        return new CancelCheckoutCommand(
                cartId,
                userId,
                request.checkoutId(),
                request.reason(),
                request.ap2SecurityLock()
        );
    }

    private static PaymentInstrument paymentInstrument(CompleteCheckoutRequest.PaymentInstrumentRequest request) {
        return new PaymentInstrument(
                request.handler(),
                request.amountMinor(),
                request.currency(),
                new PaymentCredential(
                        request.credential().type(),
                        request.credential().token(),
                        paymentCredentialDetails(request.credential().details())
                ),
                new PaymentScaLiability(
                        request.scaLiability().liableParty(),
                        request.scaLiability().liabilityShifted(),
                        request.scaLiability().challengeRequired(),
                        request.scaLiability().reason()
                )
        );
    }

    private static CompleteCheckoutCommand.Ap2MandateCommand ap2MandateCommand(
            CompleteCheckoutRequest.Ap2MandateRequest request
    ) {
        if (request == null) {
            return null;
        }
        return new CompleteCheckoutCommand.Ap2MandateCommand(
                jsonWebKey(request.merchantPublicJwk()),
                request.expectedMerchantAuthorizationKid(),
                request.merchantAuthorizationIssuer(),
                request.agentIssuer(),
                request.audience(),
                request.nonce(),
                request.expiresAt(),
                request.merchantAuthorizationJws()
        );
    }

    private static TokenPaymentCredentialDetails paymentCredentialDetails(
            CompleteCheckoutRequest.PaymentCredentialDetailsRequest request
    ) {
        return new TokenPaymentCredentialDetails(request.source());
    }

    private static JsonWebKey jsonWebKey(CompleteCheckoutRequest.JsonWebKeyRequest request) {
        return new JsonWebKey(
                request.kty(),
                request.kid(),
                request.crv(),
                request.x(),
                request.y(),
                request.n(),
                request.e(),
                request.alg(),
                request.use(),
                request.keyOps()
        );
    }

    private static CompleteCheckoutCommand.CheckoutSignalsCommand checkoutSignalsCommand(
            CompleteCheckoutRequest.CheckoutSignalsRequest request
    ) {
        if (request == null) {
            return null;
        }
        return new CompleteCheckoutCommand.CheckoutSignalsCommand(
                request.checkoutSurface(),
                request.userAgent()
        );
    }

}
