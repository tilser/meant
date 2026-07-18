package com.meant.api.module.cart.service;

import static com.meant.api.common.util.CollectionUtils.safeList;
import static com.meant.api.common.util.CollectionUtils.safeNonNullList;

import com.meant.api.module.cart.constant.CartSnapshotPurpose;
import com.meant.api.module.cart.entity.Cart;
import com.meant.api.module.cart.entity.CartLine;
import com.meant.api.module.cart.exception.CartException;
import com.meant.api.module.cart.exception.CommerceTransportFailure;
import com.meant.api.module.cart.service.command.CancelCheckoutCommand;
import com.meant.api.module.cart.service.command.CancelCartCommand;
import com.meant.api.module.cart.service.command.CompleteCheckoutCommand;
import com.meant.api.module.cart.service.command.CreateCartCommand;
import com.meant.api.module.cart.service.command.CreateCheckoutConsentCommand;
import com.meant.api.module.cart.service.command.UpdateCartCommand;
import com.meant.api.module.cart.service.command.UpdateCheckoutCommand;
import com.meant.api.module.cart.service.dto.CartResult;
import com.meant.api.module.cart.service.dto.CartRoutingTarget;
import com.meant.api.module.cart.service.dto.CartToolCallContext;
import com.meant.api.module.cart.service.dto.CartBuyerIdentityInput;
import com.meant.api.module.cart.service.dto.CartDeliveryAddressInput;
import com.meant.api.module.cart.service.dto.CartDeliveryAddressSelectionInput;
import com.meant.api.module.cart.service.dto.CartDeliveryOptionSelectionInput;
import com.meant.api.module.cart.service.dto.CheckoutConsentResult;
import com.meant.api.module.cart.service.dto.CheckoutCompletionResult;
import com.meant.api.module.cart.service.dto.CheckoutResult;
import com.meant.api.module.cart.service.query.GetCartQuery;
import com.meant.api.module.cart.service.query.GetCheckoutQuery;
import com.meant.api.module.merchant.constant.CommerceOperation;
import com.meant.api.module.merchant.service.MerchantCartProviderLookupService;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.module.user.service.UserSelectedOfferResolutionService;
import com.meant.api.module.user.exception.SelectedOfferResolutionException;
import com.meant.api.module.user.service.UserCommerceContextService;
import com.meant.api.module.user.service.dto.UserCommerceContextResult;
import com.meant.api.module.user.service.dto.ResolvedSelectedOffer;
import com.meant.api.module.user.service.query.ResolveUserSelectedOffersQuery;
import com.meant.api.plugin.cart.common.dto.CartAddItem;
import com.meant.api.plugin.cart.common.dto.CartBuyer;
import com.meant.api.plugin.cart.common.dto.CartContext;
import com.meant.api.plugin.cart.common.dto.CartDeliveryAddress;
import com.meant.api.plugin.cart.common.dto.CartDeliveryAddressSelection;
import com.meant.api.plugin.cart.common.dto.CartDeliveryOptionSelection;
import com.meant.api.plugin.cart.common.dto.CartUpdateItem;
import com.meant.api.plugin.cart.common.dto.UcpCartToolResult;
import com.meant.api.plugin.cart.common.dto.UcpCartResponse;
import com.meant.api.module.cart.service.MerchantCartPluginDispatchService;
import com.meant.api.plugin.cart.create.dto.CreateCartRequest;
import com.meant.api.plugin.cart.get.dto.GetCartRequest;
import com.meant.api.plugin.cart.update.dto.UpdateCartRequest;
import com.meant.api.plugin.cart.cancel.dto.CancelCartRequest;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutToolResult;
import com.meant.api.plugin.checkout.common.dto.CheckoutBuyer;
import com.meant.api.plugin.checkout.common.dto.CheckoutContext;
import com.meant.api.plugin.checkout.extension.fulfillment.dto.CheckoutFulfillment;
import com.meant.api.plugin.checkout.extension.fulfillment.dto.CheckoutFulfillment.FulfillmentGroup;
import com.meant.api.plugin.checkout.extension.fulfillment.dto.CheckoutFulfillment.FulfillmentMethod;
import com.meant.api.plugin.checkout.extension.fulfillment.dto.CheckoutFulfillment.ShippingDestination;
import com.meant.api.module.checkout.service.MerchantCheckoutPluginDispatchService;
import com.meant.api.module.checkout.service.NativeCheckoutCompletionService;
import com.meant.api.module.checkout.service.CheckoutPurchaseAttributionService;
import com.meant.api.module.checkout.constant.CheckoutAttributionRail;
import com.meant.api.module.checkout.constant.CheckoutAttributionTrigger;
import com.meant.api.module.checkout.service.command.NativeCheckoutCompletionCommand;
import com.meant.api.module.checkout.service.command.RecordCheckoutOpenedCommand;
import com.meant.api.module.checkout.service.dto.NativeCheckoutResult;
import com.meant.api.module.checkout.service.dto.NativeCheckoutStatus;
import com.meant.api.module.checkout.service.dto.CheckoutToolCallContext;
import com.meant.api.plugin.checkout.complete.dto.CheckoutSignals;
import com.meant.api.plugin.checkout.create.dto.CreateCheckoutRequest;
import com.meant.api.plugin.checkout.get.dto.GetCheckoutRequest;
import com.meant.api.plugin.checkout.update.dto.UpdateCheckoutRequest;
import com.meant.api.plugin.support.UcpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.service.dto.MerchantExecutionPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor(onConstructor_ = @org.springframework.beans.factory.annotation.Autowired)
@Slf4j
public class CartService {

    private final MerchantCartProviderLookupService merchantCartProviderLookupService;
    private final CartBuyerContextService cartBuyerContextService;
    private final CartPersistenceService cartPersistenceService;
    private final MerchantCartPluginDispatchService merchantCartPluginDispatchService;
    private final MerchantCheckoutPluginDispatchService merchantCheckoutPluginDispatchService;
    private final NativeCheckoutCompletionService nativeCheckoutCompletionService;
    private final CheckoutPurchaseAttributionService checkoutPurchaseAttributionService;
    private final CartResultMapper cartResultMapper;
    private final CheckoutResultMapper checkoutResultMapper;
    private final CartCheckoutConsentService cartCheckoutConsentService;
    private final UserSelectedOfferResolutionService selectedOfferResolutionService;
    private final SelectedOfferCartRoutingService selectedOfferCartRoutingService;
    private final CartOfferRevalidationService cartOfferRevalidationService;
    private final CartBindingMetrics cartBindingMetrics;
    private final UserCommerceContextService userCommerceContextService;
    private final CartReplacementService cartReplacementService;
    private final CommerceMutationPolicy commerceMutationPolicy;
    private final CheckoutUpdateReconciliationService checkoutUpdateReconciliationService;
    private final CheckoutCancellationPolicy checkoutCancellationPolicy;

    public CartResult create(@NotNull @Valid CreateCartCommand command) {
        UserCommerceContextResult commerceContext = userCommerceContextService.find(command.userId());
        ResolvedItems resolved = resolveCreate(command.userId(), command.addItems(), commerceContext);
        validateExactVariantSelections(resolved.offers());
        CartRoutingTarget target = routingTarget(resolved.offers());
        validateUnambiguousConfigurations(List.of(), resolved.offers());
        CreateCartRequest request = createCartRequest(command, resolved, commerceContext);
        UcpSession session = UcpSession.start();
        UcpCartToolResult result = merchantCartPluginDispatchService.createCart(
                target, request, session, CartToolCallContext.forBuyer(command.buyerIp()));
        return cartResultMapper.from(cartPersistenceService.saveSnapshot(
                (Cart) null,
                command.userId(),
                target,
                result,
                request.giftCardCodes(),
                resolved.offers(),
                CartSnapshotPurpose.CART_MUTATION
        ), result.response());
    }

    public CartResult get(@NotNull @Valid GetCartQuery query) {
        Cart cart = findCart(query.cartId(), query.userId());
        if (!query.refresh()) {
            return cartResultMapper.from(cart);
        }
        UcpSession session = session(cart);
        GetCartRequest request = new GetCartRequest(cart.getRemoteCartId());
        CartRoutingTarget target = routingTarget(cart);
        UcpCartToolResult result = merchantCartPluginDispatchService.getCart(
                target, request, session, CartToolCallContext.forBuyer(query.buyerIp()));
        return cartResultMapper.from(cartPersistenceService.saveSnapshot(
                cart, query.userId(), target, result, null, List.of(), CartSnapshotPurpose.READ_REFRESH),
                result.response());
    }

    public CartResult update(@NotNull @Valid UpdateCartCommand command) {
        Cart cart = findCart(command.cartId(), command.userId());
        cartReplacementService.validateIdentifiers(cart, command);
        UserCommerceContextResult commerceContext = userCommerceContextService.find(command.userId());
        ResolvedItems resolved = resolveUpdate(command.userId(), command.addItems(), commerceContext);
        validateExactVariantSelections(resolved.offers());
        CartRoutingTarget target = routingTarget(cart);
        cartOfferRevalidationService.revalidate(cart, commerceContext.countryCode());
        if (!resolved.offers().isEmpty()
                && !routingTarget(resolved.offers()).scopeKey().equals(target.scopeKey())) {
            cartBindingMetrics.record(CartException.BindingFailure.CROSS_SCOPE_REPLAY);
            throw CartException.binding(
                    CartException.BindingFailure.CROSS_SCOPE_REPLAY,
                    "Selected offer belongs to a different merchant/provider cart");
        }
        validateUnambiguousConfigurations(cart.getLines(), resolved.offers());
        UcpSession session = session(cart);
        CartToolCallContext callContext = CartToolCallContext.forBuyer(command.buyerIp());
        UcpCartResponse currentRemote = providerBound(cart)
                ? merchantCartPluginDispatchService.getCart(
                        target, new GetCartRequest(cart.getRemoteCartId()), session, callContext).response()
                : null;
        List<CartAddItem> addedItems = resolved.items().entrySet().stream()
                .map(entry -> cartAddItem(resolved.byKey().get(entry.getKey()), entry.getValue()))
                .toList();
        UpdateCartRequest request = cartReplacementService.build(
                cart, command, addedItems, cartBuyerContextService.buyerContext(commerceContext), currentRemote);
        UcpCartToolResult result;
        try {
            result = merchantCartPluginDispatchService.updateCart(target, request, session, callContext);
        } catch (CartException exception) {
            if (!commerceMutationPolicy.requiresReconciliation(exception) || request.replacementState() == null) {
                throw exception;
            }
            UcpCartToolResult reconciled = merchantCartPluginDispatchService.getCart(
                    target, new GetCartRequest(cart.getRemoteCartId()), session, callContext);
            if (!cartReplacementService.proves(reconciled.response(), request.replacementState())) {
                throw exception;
            }
            result = reconciled;
        }
        return cartResultMapper.from(cartPersistenceService.saveSnapshot(
                cart,
                command.userId(),
                target,
                result,
                request.giftCardCodes(),
                resolved.offers(),
                CartSnapshotPurpose.CART_MUTATION
        ), result.response());
    }

    private boolean providerBound(Cart cart) {
        return hasText(cart.getRoutingScopeKey()) && !cart.getRoutingScopeKey().startsWith("LEGACY:");
    }

    public CheckoutResult checkout(@NotNull @Valid GetCheckoutQuery query) {
        Cart cart = findCart(query.cartId(), query.userId());
        CartRoutingTarget target = checkoutRoutingTarget(cart);
        MerchantCartProvider provider = target.merchantProvider();
        if (!query.refresh() && hasText(cart.getCheckoutId())) {
            return checkoutResultMapper.from(cart, provider.executionPolicy());
        }
        UcpSession session = session(cart);
        CheckoutToolCallContext callContext = CheckoutToolCallContext.forBuyer(query.buyerIp());
        Cart checkoutCart = hasText(cart.getCheckoutId())
                ? cart
                : refreshEmptyCartBeforeCheckout(cart, query.userId(), target, session, query.buyerIp());
        UcpCheckoutToolResult result;
        if (query.refresh() && hasText(cart.getCheckoutId())) {
            result = merchantCheckoutPluginDispatchService.getCheckout(
                        target,
                        new GetCheckoutRequest(cart.getCheckoutId()),
                        session,
                        callContext);
        } else {
            CreateCheckoutRequest request = createCheckoutRequest(checkoutCart);
            try {
                result = merchantCheckoutPluginDispatchService.createCheckout(target, request, session, callContext);
            } catch (CartException exception) {
                if (!commerceMutationPolicy.prepareIdempotentRetry(exception)) {
                    throw exception;
                }
                // Shopify/UCP cart conversion is idempotent for the same cart_id.
                result = merchantCheckoutPluginDispatchService.createCheckout(
                        target, request, session, callContext.reconciliation());
            }
        }
        Cart refreshedCart = cartPersistenceService.saveCheckoutHandoff(
                checkoutCart.getId(), query.userId(), checkoutCart.getCheckoutGeneration(), result);
        return checkoutResultMapper.from(refreshedCart, result.response(), provider.executionPolicy());
    }

    private Cart refreshEmptyCartBeforeCheckout(
            Cart cart,
            UUID userId,
            CartRoutingTarget target,
            UcpSession session,
            String buyerIp
    ) {
        if (!cart.getLines().isEmpty()) {
            return cart;
        }
        UcpCartToolResult result = merchantCartPluginDispatchService.getCart(
                target,
                new GetCartRequest(cart.getRemoteCartId()),
                session,
                CartToolCallContext.forBuyer(buyerIp)
        );
        return cartPersistenceService.saveSnapshot(
                cart, userId, target, result, null, List.of(), CartSnapshotPurpose.READ_REFRESH);
    }

    public CheckoutResult updateCheckout(@NotNull @Valid UpdateCheckoutCommand command) {
        Cart cart = findCart(command.cartId(), command.userId());
        if (!hasText(cart.getCheckoutId())) {
            throw new CartException("Checkout session is required before updating checkout");
        }
        CartRoutingTarget target = checkoutRoutingTarget(cart);
        MerchantCartProvider provider = target.merchantProvider();
        UcpSession session = session(cart);
        CheckoutToolCallContext callContext = CheckoutToolCallContext.forBuyer(command.buyerIp());
        UcpCheckoutToolResult currentCheckout = merchantCheckoutPluginDispatchService.getCheckout(
                target,
                new GetCheckoutRequest(cart.getCheckoutId()),
                session,
                callContext
        );
        List<UpdateCheckoutRequest.LineItem> lineItems = updateCheckoutLineItems(
                cart,
                currentCheckout.response()
        );
        CheckoutBuyer buyer = checkoutBuyer(command.buyer());
        ShippingDestination shippingAddress = shippingDestination(command.buyer(), command.shippingAddress());
        CheckoutContext context = checkoutContext(cartBuyerContextService.buyerContext(command.userId()));
        List<String> discountCodes = normalizeCodes(command.discountCodes());
        UpdateCheckoutRequest updateRequest = new UpdateCheckoutRequest(
                        cart.getCheckoutId(),
                        lineItems,
                        buyer,
                        null,
                        command.buyer().email(),
                        cart.getCurrency(),
                        context,
                        discountCodes,
                        fulfillment(command.buyer(), command.shippingAddress(), lineItems));
        UcpCheckoutToolResult result;
        try {
            result = merchantCheckoutPluginDispatchService.updateCheckout(
                    target, updateRequest, session, callContext);
        } catch (CartException exception) {
            if (!commerceMutationPolicy.requiresReconciliation(exception)) {
                throw exception;
            }
            result = merchantCheckoutPluginDispatchService.getCheckout(
                    target, new GetCheckoutRequest(cart.getCheckoutId()), session, callContext.reconciliation());
            if (!checkoutUpdateReconciliationService.proves(updateRequest, result.response())) {
                throw exception;
            }
        }
        // A field-validation rejection (e.g. buyer_identity_email_is_invalid) means the merchant
        // discarded this update. Refreshing the checkout here would replace the validation message
        // with the stale pre-update state (e.g. "address required"), hiding the real problem.
        if (!hasFieldValidationMessages(result.response())) {
            if (fulfillmentOptionsMissing(result.response())) {
                result = merchantCheckoutPluginDispatchService.getCheckout(
                        target,
                        new GetCheckoutRequest(cart.getCheckoutId()),
                        session,
                        callContext
                );
            }
            CheckoutFulfillment defaultFulfillmentSelection =
                    defaultFulfillmentSelection(result.response(), shippingAddress);
            if (defaultFulfillmentSelection != null) {
                result = merchantCheckoutPluginDispatchService.updateCheckout(
                        target,
                        new UpdateCheckoutRequest(
                                cart.getCheckoutId(),
                                lineItems,
                                buyer,
                                null,
                                command.buyer().email(),
                                cart.getCurrency(),
                                context,
                                discountCodes,
                                defaultFulfillmentSelection
                        ),
                        session,
                        callContext
                );
            }
        }
        Cart refreshedCart = cartPersistenceService.saveCheckoutHandoff(
                cart.getId(), command.userId(), cart.getCheckoutGeneration(), result);
        return checkoutResultMapper.from(refreshedCart, result.response(), provider.executionPolicy());
    }

    private List<UpdateCheckoutRequest.LineItem> updateCheckoutLineItems(
            Cart cart,
            UcpCheckoutResponse currentResponse
    ) {
        List<UcpCheckoutResponse.CheckoutLineItem> checkoutLines = new ArrayList<>(checkoutLineItems(currentResponse));
        if (checkoutLines.isEmpty()) {
            checkoutLines.addAll(checkoutLineItems(cart));
        }
        List<UpdateCheckoutRequest.LineItem> lineItems = new ArrayList<>();
        for (CartLine line : cart.getLines()) {
            UcpCheckoutResponse.CheckoutLineItem checkoutLine = takeMatchingCheckoutLine(checkoutLines, line);
            String checkoutLineId = checkoutLineId(checkoutLine);
            if (!hasText(checkoutLineId)) {
                throw CartException.rejected("Checkout line identity is unavailable after refresh");
            }
            lineItems.add(new UpdateCheckoutRequest.LineItem(
                    checkoutLineId,
                    line.getProductVariantId(),
                    line.getQuantity()
            ));
        }
        if (!checkoutLines.isEmpty()) {
            throw CartException.rejected("Checkout lines no longer match the cart");
        }
        return List.copyOf(lineItems);
    }

    private List<UcpCheckoutResponse.CheckoutLineItem> checkoutLineItems(UcpCheckoutResponse response) {
        UcpCheckoutResponse.Checkout checkout = response == null ? null : response.resolvedCheckout();
        return checkout == null ? List.of() : safeList(checkout.lineItems());
    }

    private List<UcpCheckoutResponse.CheckoutLineItem> checkoutLineItems(Cart cart) {
        UcpCheckoutResponse response = checkoutResultMapper.parseStoredResponse(cart.getRawCheckoutResponse());
        UcpCheckoutResponse.Checkout checkout = response == null ? null : response.resolvedCheckout();
        return checkout == null ? List.of() : safeList(checkout.lineItems());
    }

    private UcpCheckoutResponse.CheckoutLineItem takeMatchingCheckoutLine(
            List<UcpCheckoutResponse.CheckoutLineItem> checkoutLines,
            CartLine cartLine
    ) {
        int matchingIndex = matchingCheckoutLineIndex(checkoutLines, cartLine);
        return matchingIndex < 0 ? null : checkoutLines.remove(matchingIndex);
    }

    private int matchingCheckoutLineIndex(
            List<UcpCheckoutResponse.CheckoutLineItem> checkoutLines,
            CartLine cartLine
    ) {
        for (int index = 0; index < checkoutLines.size(); index++) {
            UcpCheckoutResponse.CheckoutLineItem checkoutLine = checkoutLines.get(index);
            if (checkoutLine != null && sameVariant(cartLine, checkoutLine)) {
                return index;
            }
        }
        return -1;
    }

    private boolean sameVariant(CartLine cartLine, UcpCheckoutResponse.CheckoutLineItem checkoutLine) {
        return hasText(cartLine.getProductVariantId())
                && cartLine.getProductVariantId().equals(checkoutLine.resolvedVariantId());
    }

    private String checkoutLineId(UcpCheckoutResponse.CheckoutLineItem checkoutLine) {
        return checkoutLine == null ? null : firstText(checkoutLine.id(), checkoutLine.lineId());
    }

    private CreateCheckoutRequest createCheckoutRequest(Cart cart) {
        List<CreateCheckoutRequest.LineItem> lineItems = cart.getLines().stream()
                .map(line -> new CreateCheckoutRequest.LineItem(
                        null,
                        line.getProductVariantId(),
                        line.getQuantity()
                ))
                .toList();
        if (lineItems.isEmpty()) {
            throw CartException.rejected("Checkout requires at least one line item.");
        }
        return new CreateCheckoutRequest(
                cart.getRemoteCartId(),
                lineItems,
                null,
                null,
                cart.getCurrency(),
                checkoutContext(cartBuyerContextService.buyerContext(cart.getUserId())),
                List.of(),
                null
        );
    }

    public CheckoutCompletionResult completeCheckout(@NotNull @Valid CompleteCheckoutCommand command) {
        Cart cart = findCart(command.cartId(), command.userId());
        MerchantCartProvider provider = findProvider(cart.getMerchantId(), cart.getMerchantDomain());
        Cart checkoutCart = ensureHandoffWhenDirectCompletionUnavailable(
                cart,
                command.userId(),
                provider,
                command.ap2SecurityLock(),
                command.buyerIp()
        );
        NativeCheckoutResult result = nativeCheckoutCompletionService.complete(
                provider,
                nativeCompletionCommand(command),
                session(checkoutCart)
        );
        if (result.status() == NativeCheckoutStatus.COMPLETED) {
            // Provider completion is authoritative; inventory projection must not turn it into a payment failure.
            try {
                checkoutPurchaseAttributionService.record(new RecordCheckoutOpenedCommand(
                        command.userId(),
                        checkoutCart.getId(),
                        checkoutCart.getCheckoutAttemptId(),
                        CheckoutAttributionRail.NATIVE_CHECKOUT,
                        CheckoutAttributionTrigger.VERIFIED_COMPLETION,
                        null
                ));
            } catch (RuntimeException exception) {
                log.error(
                        "Inventory attribution failed after verified native checkout completion "
                                + "cartId={} userId={} checkoutAttemptId={}",
                        checkoutCart.getId(),
                        command.userId(),
                        checkoutCart.getCheckoutAttemptId(),
                        exception
                );
            }
        }
        return completionResult(checkoutCart, result);
    }

    public CheckoutConsentResult recordCheckoutConsent(@NotNull @Valid CreateCheckoutConsentCommand command) {
        Cart cart = findCart(command.cartId(), command.userId());
        return cartCheckoutConsentService.recordConsent(cart, command);
    }

    public CheckoutCompletionResult cancelCheckout(@NotNull @Valid CancelCheckoutCommand command) {
        Cart cart = findCart(command.cartId(), command.userId());
        if (!hasText(cart.getCheckoutId()) || !cart.getCheckoutId().equals(command.checkoutId())) {
            throw CartException.rejected("Checkout session does not match cart");
        }
        CartRoutingTarget target = checkoutRoutingTarget(cart);
        UcpSession session = session(cart);
        UUID idempotencyKey = UUID.nameUUIDFromBytes(
                ("cancel_checkout:" + cart.getId() + ':' + command.checkoutId())
                        .getBytes(StandardCharsets.UTF_8));
        CheckoutToolCallContext callContext = new CheckoutToolCallContext(
                idempotencyKey, true, command.buyerIp());
        UcpCheckoutToolResult result;
        try {
            result = merchantCheckoutPluginDispatchService.cancelCheckout(
                    target, new com.meant.api.plugin.checkout.cancel.dto.CancelCheckoutRequest(
                            command.checkoutId(), command.reason()), session, callContext);
        } catch (CartException exception) {
            if (!commerceMutationPolicy.requiresReconciliation(exception)) {
                throw exception;
            }
            result = merchantCheckoutPluginDispatchService.getCheckout(
                    target, new GetCheckoutRequest(command.checkoutId()), session, callContext.reconciliation());
        }
        checkoutCancellationPolicy.requireCancelled(result.response());
        Cart refreshed = cartPersistenceService.saveCheckoutHandoff(
                cart.getId(), command.userId(), cart.getCheckoutGeneration(), result);
        return new CheckoutCompletionResult(
                refreshed.getId(), refreshed.getRemoteCartId(), NativeCheckoutStatus.CANCELED,
                refreshed.getCheckoutId(), null, refreshed.getContinueUrl(), checkoutMessages(result.response()), false);
    }

    private List<String> checkoutMessages(UcpCheckoutResponse response) {
        if (response == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>(Stream.concat(response.messages().stream(),
                        response.resolvedCheckout() == null ? Stream.empty()
                                : response.resolvedCheckout().messages().stream())
                .map(UcpCheckoutResponse.CheckoutMessage::message)
                .filter(this::hasText)
                .toList());
        response.errors().stream().map(UcpCheckoutResponse.CheckoutError::message)
                .filter(this::hasText).forEach(values::add);
        return List.copyOf(values);
    }

    public void cancel(@NotNull @Valid CancelCartCommand command) {
        Cart cart = findCart(command.cartId(), command.userId());
        CancelCartRequest request = new CancelCartRequest(cart.getRemoteCartId());
        CartRoutingTarget target = routingTarget(cart);
        merchantCartPluginDispatchService.cancelCart(
                target, request, session(cart), new CartToolCallContext(cart.getId(), command.buyerIp()));
        cartPersistenceService.deactivate(cart.getId(), command.userId());
    }

    private MerchantCartProvider findProvider(UUID merchantId, String merchantDomain) {
        if (merchantId != null) {
            return merchantCartProviderLookupService.findById(merchantId)
                    .orElseThrow(() -> CartException.notFound("Merchant not found: " + merchantId));
        }
        if (merchantDomain != null && !merchantDomain.isBlank()) {
            return merchantCartProviderLookupService.findByDomain(merchantDomain)
                    .orElseThrow(() -> CartException.notFound("Merchant not found: " + merchantDomain));
        }
        throw new CartException("merchantId or merchantDomain is required");
    }

    private Cart findCart(UUID cartId, UUID userId) {
        return cartPersistenceService.findCart(cartId, userId);
    }

    private CreateCartRequest createCartRequest(
            CreateCartCommand command, ResolvedItems resolved, UserCommerceContextResult commerceContext) {
        return new CreateCartRequest(
                resolved.items().entrySet().stream()
                        .map(entry -> cartAddItem(resolved.byKey().get(entry.getKey()), entry.getValue()))
                        .toList(),
                cartBuyer(command.buyerIdentity()),
                cartContext(commerceContext, command.buyerIdentity()),
                cartDeliveryAddresses(command.deliveryAddressesToAdd()),
                cartDeliveryAddresses(command.deliveryAddressesToReplace()),
                cartDeliveryOptions(command.selectedDeliveryOptions()),
                normalizeCodes(command.discountCodes()),
                normalizeCodes(command.giftCardCodes()),
                command.note()
        );
    }

    private CartBuyer cartBuyer(CartBuyerIdentityInput buyer) {
        return buyer == null ? null : new CartBuyer(
                trimToNull(buyer.firstName()),
                trimToNull(buyer.lastName()),
                trimToNull(buyer.email()),
                trimToNull(buyer.phoneNumber())
        );
    }

    private CartContext cartContext(
            UserCommerceContextResult commerceContext,
            CartBuyerIdentityInput buyer
    ) {
        CartContext context = cartBuyerContextService.buyerContext(commerceContext);
        String buyerCountry = buyer == null ? null : trimToNull(buyer.countryCode());
        return buyerCountry == null ? context : context.merge(new CartContext(buyerCountry.toUpperCase(Locale.ROOT)));
    }

    private List<CartDeliveryAddressSelection> cartDeliveryAddresses(
            List<CartDeliveryAddressSelectionInput> inputs
    ) {
        return safeNonNullList(inputs).stream()
                .map(input -> new CartDeliveryAddressSelection(
                        trimToNull(input.methodId()), input.selected(), cartDeliveryAddress(input)))
                .toList();
    }

    private CartDeliveryAddress cartDeliveryAddress(CartDeliveryAddressSelectionInput input) {
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

    private List<CartDeliveryOptionSelection> cartDeliveryOptions(
            List<CartDeliveryOptionSelectionInput> inputs
    ) {
        return safeNonNullList(inputs).stream()
                .map(input -> new CartDeliveryOptionSelection(
                        trimToNull(input.methodId()), trimToNull(input.groupId()), trimToNull(input.selectedOptionId())))
                .toList();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private CheckoutContext checkoutContext(CartContext context) {
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

    private Cart ensureHandoffWhenDirectCompletionUnavailable(
            Cart cart,
            UUID userId,
            MerchantCartProvider provider,
            boolean ap2SecurityLock,
            String buyerIp
    ) {
        if (provider.executionPolicy().isAvailable(CommerceOperation.DIRECT_CHECKOUT_COMPLETION)
                || ap2SecurityLock
                || hasText(handoffUrl(cart))) {
            return cart;
        }
        UcpCheckoutToolResult result = merchantCheckoutPluginDispatchService.createCheckout(
                checkoutRoutingTarget(cart),
                createCheckoutRequest(cart),
                session(cart),
                CheckoutToolCallContext.forBuyer(buyerIp)
        );
        return cartPersistenceService.saveCheckoutHandoff(
                cart.getId(), userId, cart.getCheckoutGeneration(), result);
    }

    private CheckoutBuyer checkoutBuyer(UpdateCheckoutCommand.Buyer buyer) {
        return new CheckoutBuyer(
                trimToNull(buyer.firstName()),
                trimToNull(buyer.lastName()),
                trimToNull(buyer.email()),
                trimToNull(buyer.phoneNumber())
        );
    }

    private CheckoutFulfillment fulfillment(
            UpdateCheckoutCommand.Buyer buyer,
            UpdateCheckoutCommand.PostalAddress address,
            List<UpdateCheckoutRequest.LineItem> lineItems
    ) {
        ShippingDestination destination = shippingDestination(buyer, address);
        List<String> lineItemIds = lineItemIds(lineItems);
        FulfillmentMethod method = new FulfillmentMethod(
                "shipping",
                "shipping",
                lineItemIds,
                List.of(destination),
                "shipping",
                List.of()
        );
        return new CheckoutFulfillment(List.of(method));
    }

    private CheckoutFulfillment defaultFulfillmentSelection(
            UcpCheckoutResponse response,
            ShippingDestination destination
    ) {
        UcpCheckoutResponse.Checkout checkout = response == null ? null : response.resolvedCheckout();
        UcpCheckoutResponse.CheckoutFulfillment fulfillment = checkout == null ? null : checkout.fulfillment();
        if (fulfillment == null || fulfillment.methods().isEmpty()) {
            return null;
        }
        List<FulfillmentMethod> methods = fulfillment.methods().stream()
                .map(method -> defaultFulfillmentMethodSelection(method, destination))
                .filter(Objects::nonNull)
                .toList();
        return methods.isEmpty() ? null : new CheckoutFulfillment(methods);
    }

    private FulfillmentMethod defaultFulfillmentMethodSelection(
            UcpCheckoutResponse.CheckoutFulfillmentMethod method,
            ShippingDestination destination
    ) {
        if (method == null || method.groups().isEmpty()) {
            return null;
        }
        List<FulfillmentGroup> groups = method.groups().stream()
                .map(this::defaultFulfillmentGroupSelection)
                .filter(Objects::nonNull)
                .toList();
        if (groups.isEmpty()) {
            return null;
        }
        // Update checkout uses replacement semantics, so the selection must carry the
        // destination again or the merchant may drop the shipping address.
        return new FulfillmentMethod(
                firstText(method.id(), method.type()),
                trimToNull(method.type()),
                normalizedStrings(method.lineItemIds()),
                destination == null ? List.of() : List.of(destination),
                selectedDestinationId(method),
                groups
        );
    }

    private boolean hasFieldValidationMessages(UcpCheckoutResponse response) {
        if (response == null) {
            return false;
        }
        return Stream.concat(
                        safeNonNullList(response.messages()).stream(),
                        response.resolvedCheckout() == null
                                ? Stream.empty()
                                : safeNonNullList(response.resolvedCheckout().messages()).stream()
                )
                .anyMatch(this::isFieldValidationMessage);
    }

    private boolean isFieldValidationMessage(UcpCheckoutResponse.CheckoutMessage message) {
        if (message == null) {
            return false;
        }
        String code = message.code() == null ? "" : message.code().trim().toLowerCase(Locale.ROOT);
        if (code.startsWith("buyer_identity")) {
            return true;
        }
        String target = message.target() == null ? "" : message.target().trim().toLowerCase(Locale.ROOT);
        return target.startsWith("$.buyer");
    }

    private FulfillmentGroup defaultFulfillmentGroupSelection(UcpCheckoutResponse.CheckoutFulfillmentGroup group) {
        if (group == null || hasText(group.selectedOptionId())) {
            return null;
        }
        String selectedOptionId = defaultOptionId(group);
        if (!hasText(group.id()) || !hasText(selectedOptionId)) {
            return null;
        }
        return new FulfillmentGroup(
                group.id().trim(),
                normalizedStrings(group.lineItemIds()),
                List.of(),
                selectedOptionId.trim()
        );
    }

    private String selectedDestinationId(UcpCheckoutResponse.CheckoutFulfillmentMethod method) {
        if (hasText(method.selectedDestinationId())) {
            return method.selectedDestinationId().trim();
        }
        return method.destinations().stream()
                .map(UcpCheckoutResponse.CheckoutAddress::id)
                .filter(this::hasText)
                .findFirst()
                .orElse(null);
    }

    private String defaultOptionId(UcpCheckoutResponse.CheckoutFulfillmentGroup group) {
        String selected = group.selectedOption();
        if (hasText(selected)) {
            return selected.trim();
        }
        return group.options().stream()
                .map(UcpCheckoutResponse.CheckoutFulfillmentOption::resolvedId)
                .filter(this::hasText)
                .findFirst()
                .orElse(null);
    }

    private List<String> lineItemIds(List<UpdateCheckoutRequest.LineItem> lineItems) {
        return safeList(lineItems).stream()
                .map(UpdateCheckoutRequest.LineItem::id)
                .filter(this::hasText)
                .map(String::trim)
                .toList();
    }

    private ShippingDestination shippingDestination(
            UpdateCheckoutCommand.Buyer buyer,
            UpdateCheckoutCommand.PostalAddress address
    ) {
        return new ShippingDestination(
                "shipping",
                trimToNull(address.extendedAddress()),
                trimToNull(address.streetAddress()),
                trimToNull(address.addressLocality()),
                trimToNull(address.addressRegion()),
                trimToNull(address.addressCountry()),
                trimToNull(address.postalCode()),
                trimToNull(buyer.firstName()),
                trimToNull(buyer.lastName()),
                trimToNull(buyer.phoneNumber())
        );
    }

    private boolean fulfillmentOptionsMissing(UcpCheckoutResponse response) {
        UcpCheckoutResponse.Checkout checkout = response == null ? null : response.resolvedCheckout();
        if (checkout == null) {
            return false;
        }
        UcpCheckoutResponse.CheckoutFulfillment fulfillment = checkout.fulfillment();
        if (fulfillment == null || fulfillment.methods().isEmpty()) {
            return true;
        }
        return fulfillment.methods().stream()
                .allMatch(method -> method == null || method.groups().isEmpty());
    }

    private List<String> normalizedStrings(List<String> values) {
        return safeList(values).stream()
                .filter(this::hasText)
                .map(String::trim)
                .toList();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private NativeCheckoutCompletionCommand nativeCompletionCommand(CompleteCheckoutCommand command) {
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

    private NativeCheckoutCompletionCommand.Ap2MandateInput ap2MandateInput(
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

    private CheckoutSignals checkoutSignals(CompleteCheckoutCommand.CheckoutSignalsCommand command) {
        if (command == null) {
            return null;
        }
        return new CheckoutSignals(command.checkoutSurface(), command.userAgent());
    }

    private CheckoutCompletionResult completionResult(Cart cart, NativeCheckoutResult result) {
        return new CheckoutCompletionResult(
                cart.getId(),
                cart.getRemoteCartId(),
                result.status(),
                result.checkoutId(),
                result.orderRef(),
                result.continueUrl(),
                result.messages(),
                result.nativeAttempted()
        );
    }

    private ResolvedItems resolveCreate(
            UUID userId, List<CreateCartCommand.AddItem> items, UserCommerceContextResult context) {
        return resolve(userId, safeList(items).stream()
                .map(item -> new SelectedOfferQuantity(item.offerKey(), item.quantity())).toList(), context);
    }

    private ResolvedItems resolveUpdate(
            UUID userId, List<UpdateCartCommand.AddItem> items, UserCommerceContextResult context) {
        return resolve(userId, safeList(items).stream()
                .map(item -> new SelectedOfferQuantity(item.offerKey(), item.quantity())).toList(), context);
    }

    private ResolvedItems resolve(
            UUID userId, List<SelectedOfferQuantity> rawItems, UserCommerceContextResult context) {
        Map<String, Integer> quantities = new LinkedHashMap<>();
        for (SelectedOfferQuantity rawItem : safeList(rawItems)) {
                String offerKey = rawItem.offerKey();
                Integer quantity = rawItem.quantity();
                int normalizedQuantity = quantity == null ? 1 : quantity;
                if (quantities.putIfAbsent(offerKey, normalizedQuantity) != null) {
                    quantities.merge(offerKey, normalizedQuantity, Integer::sum);
                    cartBindingMetrics.record(CartException.BindingFailure.IDEMPOTENT_REPLAY);
                }
        }
        Map<String, ResolvedSelectedOffer> byKey = new LinkedHashMap<>();
        List<String> offerKeys = List.copyOf(quantities.keySet());
        if (offerKeys.isEmpty()) {
            return new ResolvedItems(quantities, byKey);
        }
        List<ResolvedSelectedOffer> resolvedOffers = selectedOfferResolutionService.resolveAll(
                new ResolveUserSelectedOffersQuery(userId, offerKeys, context.countryCode()));
        if (resolvedOffers.size() != offerKeys.size()) {
            cartBindingMetrics.record(CartException.BindingFailure.PROVIDER_FAILURE);
            throw CartException.binding(
                    CartException.BindingFailure.PROVIDER_FAILURE,
                    "Selected offer resolution returned an incomplete result set");
        }
        java.util.stream.IntStream.range(0, offerKeys.size())
                .forEach(index -> byKey.put(offerKeys.get(index), resolvedOffers.get(index)));
        return new ResolvedItems(quantities, byKey);
    }

    private CartRoutingTarget routingTarget(List<ResolvedSelectedOffer> offers) {
        if (offers.isEmpty()) {
            throw new CartException("At least one selected offer is required");
        }
        Map<String, ResolvedSelectedOffer> uniqueRoutes = new LinkedHashMap<>();
        offers.forEach(offer -> uniqueRoutes.putIfAbsent(routeSelectionKey(offer), offer));
        List<CartRoutingTarget> targets = uniqueRoutes.values().stream()
                .map(selectedOfferCartRoutingService::resolve)
                .toList();
        String scope = targets.getFirst().scopeKey();
        if (targets.stream().anyMatch(target -> !scope.equals(target.scopeKey()))) {
            cartBindingMetrics.record(CartException.BindingFailure.CROSS_SCOPE_REPLAY);
            throw CartException.binding(
                    CartException.BindingFailure.CROSS_SCOPE_REPLAY,
                    "One remote cart cannot mix sellers or provider integrations");
        }
        return targets.getFirst();
    }

    private void validateExactVariantSelections(List<ResolvedSelectedOffer> offers) {
        if (safeList(offers).stream().anyMatch(offer -> offer.identity().externalVariantIdentity() == null)) {
            throw SelectedOfferResolutionException.rejected(
                    SelectedOfferResolutionException.Failure.UNSUPPORTED_SELECTION,
                    "Cart selections require an exact variant");
        }
    }

    private String routeSelectionKey(ResolvedSelectedOffer offer) {
        var local = offer.rehydratedReference().localRouting();
        if (local != null) {
            return offer.identity().provider().value() + ":integration:" + local.merchantIntegrationId();
        }
        var merchant = offer.identity().merchantScope().externalMerchantIdentity();
        return offer.identity().provider().value() + ":merchant:" + (merchant == null ? "missing" : merchant.value());
    }

    private CartRoutingTarget routingTarget(Cart cart) {
        return routingTarget(cart, false);
    }

    private CartRoutingTarget checkoutRoutingTarget(Cart cart) {
        return routingTarget(cart, true);
    }

    private CartRoutingTarget routingTarget(Cart cart, boolean checkoutPolicyRequired) {
        if (!hasText(cart.getProvider()) || !hasText(cart.getRoutingScopeKey())
                || cart.getRoutingScopeKey().startsWith("LEGACY:")) {
            MerchantCartProvider legacy = findProvider(cart.getMerchantId(), cart.getMerchantDomain());
            return new CartRoutingTarget(
                    "LEGACY:merchant:" + cart.getMerchantId(),
                    MerchantIntegrationProvider.GENERIC_UCP,
                    null,
                    null,
                    legacy
            );
        }
        MerchantIntegrationProvider provider;
        try {
            provider = MerchantIntegrationProvider.valueOf(cart.getProvider());
        } catch (IllegalArgumentException exception) {
            throw CartException.rejected("Stored cart provider is unsupported");
        }
        if (cart.getMerchantIntegrationId() != null) {
            return selectedOfferCartRoutingService.resolvePersisted(
                    provider,
                    cart.getMerchantIntegrationId(),
                    cart.getExternalMerchantId(),
                    cart.getRoutingScopeKey()
            );
        }
        if (cart.getMerchantId() != null) {
            throw CartException.binding(
                    CartException.BindingFailure.MISSING_ROUTING,
                    "Stored cart is missing its immutable provider integration"
            );
        }
        CartRoutingTarget persistedTarget = new CartRoutingTarget(
                cart.getRoutingScopeKey(), provider, cart.getMerchantIntegrationId(),
                cart.getExternalMerchantId(), new MerchantCartProvider(
                        null, cart.getMerchantDomain(), cart.getEndpoint(), null,
                        List.of(), MerchantExecutionPolicy.unavailable()));
        return checkoutPolicyRequired
                ? selectedOfferCartRoutingService.resolvePersistedExternalForCheckout(persistedTarget)
                : selectedOfferCartRoutingService.resolvePersistedExternal(persistedTarget);
    }

    private CartAddItem cartAddItem(ResolvedSelectedOffer offer, Integer quantity) {
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

    private record ResolvedItems(
            Map<String, Integer> items,
            Map<String, ResolvedSelectedOffer> byKey
    ) {
        private List<ResolvedSelectedOffer> offers() {
            return List.copyOf(byKey.values());
        }
    }

    private record SelectedOfferQuantity(String offerKey, Integer quantity) {
    }

    private void validateUnambiguousConfigurations(
            List<CartLine> existingLines, List<ResolvedSelectedOffer> addedOffers) {
        Map<String, java.util.Set<String>> offerKeysByVariant = new LinkedHashMap<>();
        safeList(existingLines).stream()
                .filter(line -> line.getOfferKey() != null && hasText(line.getExternalVariantId()))
                .forEach(line -> offerKeysByVariant
                        .computeIfAbsent(line.getExternalVariantId(), ignored -> new java.util.HashSet<>())
                        .add(line.getOfferKey()));
        safeList(addedOffers).forEach(offer -> {
            String variant = offer.identity().externalVariantIdentity() == null
                    ? null : offer.identity().externalVariantIdentity().value();
            if (hasText(variant)) {
                offerKeysByVariant.computeIfAbsent(variant, ignored -> new java.util.HashSet<>())
                        .add(offer.offerKey());
            }
        });
        if (offerKeysByVariant.values().stream().anyMatch(keys -> keys.size() > 1)) {
            cartBindingMetrics.record(CartException.BindingFailure.IDENTITY_MISMATCH);
            throw CartException.binding(
                    CartException.BindingFailure.IDENTITY_MISMATCH,
                    "Remote cart response cannot disambiguate multiple configurations of one variant");
        }
    }

    private UcpSession session(Cart cart) {
        return UcpSession.cart(cart.getRemoteCartId(), cart.getExpiresAt(), handoffUrl(cart));
    }

    private String handoffUrl(Cart cart) {
        if (hasText(cart.getContinueUrl())) {
            return cart.getContinueUrl();
        }
        return cart.getCheckoutUrl();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private List<String> normalizeCodes(List<String> codes) {
        if (codes == null) {
            return null;
        }
        return codes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

}
