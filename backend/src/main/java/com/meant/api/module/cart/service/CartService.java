package com.meant.api.module.cart.service;

import static com.meant.api.common.util.CollectionUtils.safeList;
import static com.meant.api.module.cart.service.CheckoutFulfillmentSupport.checkoutBuyer;
import static com.meant.api.module.cart.service.CheckoutFulfillmentSupport.defaultFulfillmentSelection;
import static com.meant.api.module.cart.service.CheckoutFulfillmentSupport.fulfillment;
import static com.meant.api.module.cart.service.CheckoutFulfillmentSupport.fulfillmentOptionsMissing;
import static com.meant.api.module.cart.service.CheckoutFulfillmentSupport.hasCheckoutDetailsValidationProblems;
import static com.meant.api.module.cart.service.CheckoutFulfillmentSupport.hasText;
import static com.meant.api.module.cart.service.CheckoutFulfillmentSupport.shippingDestination;
import static com.meant.api.module.cart.service.CheckoutLineItemMapper.updateLineItems;
import static com.meant.api.module.cart.service.CartProtocolMapper.cartAddItem;
import static com.meant.api.module.cart.service.CartProtocolMapper.cartBuyer;
import static com.meant.api.module.cart.service.CartProtocolMapper.cartContext;
import static com.meant.api.module.cart.service.CartProtocolMapper.cartDeliveryAddresses;
import static com.meant.api.module.cart.service.CartProtocolMapper.cartDeliveryOptions;
import static com.meant.api.module.cart.service.CartProtocolMapper.checkoutContext;
import static com.meant.api.module.cart.service.CartProtocolMapper.nativeCompletionCommand;
import static com.meant.api.module.cart.service.CartProtocolMapper.normalizeCodes;

import com.meant.api.common.service.UserMutationExecutionLane;
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
import com.meant.api.module.cart.service.dto.CartOfferPartitionResult;
import com.meant.api.module.cart.service.dto.CartRoutingTarget;
import com.meant.api.module.cart.service.dto.CartToolCallContext;
import com.meant.api.module.cart.service.dto.CheckoutConsentResult;
import com.meant.api.module.cart.service.dto.CheckoutCompletionResult;
import com.meant.api.module.cart.service.dto.CheckoutResult;
import com.meant.api.module.cart.service.query.FindActiveCartByRoutingScopeQuery;
import com.meant.api.module.cart.service.query.GetCartQuery;
import com.meant.api.module.cart.service.query.GetCheckoutQuery;
import com.meant.api.module.cart.service.query.ListActiveCartsQuery;
import com.meant.api.module.cart.service.query.PartitionSelectedOffersQuery;
import com.meant.api.module.merchant.constant.CommerceOperation;
import com.meant.api.module.merchant.service.MerchantBuyerTextSanitizer;
import com.meant.api.module.merchant.service.MerchantCartProviderLookupService;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.module.user.service.UserSelectedOfferResolutionService;
import com.meant.api.module.user.service.UserCheckoutDetailsService;
import com.meant.api.module.user.exception.SelectedOfferResolutionException;
import com.meant.api.module.user.service.UserCommerceContextService;
import com.meant.api.module.user.service.command.SaveUserCheckoutDetailsCommand;
import com.meant.api.module.user.service.dto.UserCommerceContextResult;
import com.meant.api.module.user.service.dto.UserCheckoutDetailsResult;
import com.meant.api.module.user.service.dto.ResolvedSelectedOffer;
import com.meant.api.module.user.service.query.GetUserCheckoutDetailsQuery;
import com.meant.api.module.user.service.query.ResolveUserSelectedOffersQuery;
import com.meant.api.plugin.cart.common.dto.CartAddItem;
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
import com.meant.api.plugin.checkout.extension.fulfillment.dto.CheckoutFulfillment.ShippingDestination;
import com.meant.api.module.checkout.service.MerchantCheckoutPluginDispatchService;
import com.meant.api.module.checkout.service.NativeCheckoutCompletionService;
import com.meant.api.module.checkout.service.CheckoutPurchaseAttributionService;
import com.meant.api.module.checkout.constant.CheckoutAttributionRail;
import com.meant.api.module.checkout.constant.CheckoutAttributionTrigger;
import com.meant.api.module.checkout.service.command.RecordCheckoutOpenedCommand;
import com.meant.api.module.checkout.service.dto.NativeCheckoutResult;
import com.meant.api.module.checkout.service.dto.NativeCheckoutStatus;
import com.meant.api.module.checkout.service.dto.CheckoutToolCallContext;
import com.meant.api.plugin.checkout.create.dto.CreateCheckoutRequest;
import com.meant.api.plugin.checkout.get.dto.GetCheckoutRequest;
import com.meant.api.plugin.checkout.update.dto.UpdateCheckoutRequest;
import com.meant.api.plugin.support.UcpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.service.dto.MerchantExecutionPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor(onConstructor_ = @org.springframework.beans.factory.annotation.Autowired)
@Slf4j
public class CartService {

    private static final int ACTIVE_CART_PAGE_SIZE = 20;

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
    private final UserCheckoutDetailsService userCheckoutDetailsService;
    private final UserMutationExecutionLane userMutationExecutionLane;

    public CartResult create(@NotNull @Valid CreateCartCommand command) {
        return create(command, null);
    }

    public CartResult create(@NotNull @Valid CreateCartCommand command, UUID idempotencyKey) {
        return executeMutation(
                command.userId(),
                () -> createInMutationLane(command, idempotencyKey)
        );
    }

    private CartResult createInMutationLane(CreateCartCommand command, UUID idempotencyKey) {
        UserCommerceContextResult commerceContext = userCommerceContextService.find(command.userId());
        ResolvedItems resolved = resolveCreate(command.userId(), command.addItems(), commerceContext);
        validateExactVariantSelections(resolved.offers());
        CartRoutingTarget target = routingTarget(resolved.offers());
        validateUnambiguousConfigurations(List.of(), resolved.offers());
        CreateCartRequest request = createCartRequest(command, resolved, commerceContext);
        UcpSession session = UcpSession.start();
        UcpCartToolResult result = merchantCartPluginDispatchService.createCart(
                target,
                request,
                session,
                new CartToolCallContext(idempotencyKey, command.buyerIp())
        );
        Cart persistedCart;
        try {
            persistedCart = cartPersistenceService.saveCreatedSnapshot(
                    command.userId(),
                    target,
                    result,
                    request.giftCardCodes(),
                    resolved.offers(),
                    CartSnapshotPurpose.CART_MUTATION,
                    idempotencyKey
            );
        } catch (DataIntegrityViolationException exception) {
            if (idempotencyKey == null) {
                throw exception;
            }
            persistedCart = cartPersistenceService.findCreatedSnapshot(command.userId(), target, result)
                    .orElseThrow(() -> exception);
        }
        return cartResultMapper.from(
                persistedCart,
                result.response(),
                target.merchantProvider()
        );
    }

    public CartResult get(@NotNull @Valid GetCartQuery query) {
        if (query.refresh()) {
            return executeMutation(query.userId(), () -> getInternal(query));
        }
        return getInternal(query);
    }

    private CartResult getInternal(GetCartQuery query) {
        Cart cart = findCart(query.cartId(), query.userId());
        if (!query.refresh()) {
            return storedCartResult(cart);
        }
        UcpSession session = session(cart);
        GetCartRequest request = new GetCartRequest(cart.getRemoteCartId());
        CartRoutingTarget target = routingTarget(cart);
        UcpCartToolResult result = merchantCartPluginDispatchService.getCart(
                target, request, session, CartToolCallContext.forBuyer(query.buyerIp()));
        return cartResultMapper.from(
                cartPersistenceService.saveSnapshot(
                        cart,
                        query.userId(),
                        target,
                        result,
                        null,
                        List.of(),
                        CartSnapshotPurpose.READ_REFRESH
                ),
                result.response(),
                target.merchantProvider()
        );
    }

    public List<CartResult> listActive(@NotNull @Valid ListActiveCartsQuery query) {
        Map<String, Cart> currentByRoute = new LinkedHashMap<>();
        int page = 0;
        while (currentByRoute.size() < query.limit()) {
            List<Cart> candidates = cartPersistenceService.findActiveCarts(
                    query.userId(), page++, ACTIVE_CART_PAGE_SIZE);
            candidates.forEach(cart -> currentByRoute.putIfAbsent(CartRouteKey.from(cart), cart));
            if (candidates.size() < ACTIVE_CART_PAGE_SIZE) {
                break;
            }
        }
        return currentByRoute.values().stream()
                .limit(query.limit())
                .map(this::storedCartResult)
                .toList();
    }

    public Optional<CartResult> findActiveByRoutingScope(
            @NotNull @Valid FindActiveCartByRoutingScopeQuery query
    ) {
        return cartPersistenceService.findActiveCartByRoutingScope(query.userId(), query.routingScopeKey())
                .map(this::storedCartResult);
    }

    /**
     * Resolves server-issued offer keys and groups them by the immutable remote-cart scope.
     * Resolution and routing can perform remote I/O; this method intentionally has no transaction.
     */
    public List<CartOfferPartitionResult> partitionSelectedOffers(
            @NotNull @Valid PartitionSelectedOffersQuery query
    ) {
        UserCommerceContextResult commerceContext = userCommerceContextService.find(query.userId());
        ResolvedItems resolved = resolve(
                query.userId(),
                query.items().stream()
                        .map(item -> new SelectedOfferQuantity(item.offerKey(), item.quantity()))
                        .toList(),
                commerceContext
        );
        validateExactVariantSelections(resolved.offers());
        Map<String, MutableOfferPartition> partitions = new LinkedHashMap<>();
        resolved.items().forEach((offerKey, quantity) -> {
            CartRoutingTarget target = selectedOfferCartRoutingService.resolve(resolved.byKey().get(offerKey));
            MutableOfferPartition partition = partitions.computeIfAbsent(
                    target.scopeKey(),
                    ignored -> new MutableOfferPartition(target)
            );
            partition.items().add(new CartOfferPartitionResult.Item(offerKey, quantity));
        });
        return partitions.values().stream()
                .map(partition -> new CartOfferPartitionResult(
                        partition.target().scopeKey(),
                        partition.target().provider().name(),
                        partition.target().merchantIntegrationId(),
                        partition.target().externalMerchantId(),
                        partition.target().merchantProvider().merchantId(),
                        partition.target().merchantProvider().merchantDomain(),
                        partition.items()
                ))
                .toList();
    }

    public CartResult update(@NotNull @Valid UpdateCartCommand command) {
        return update(command, null);
    }

    public CartResult update(@NotNull @Valid UpdateCartCommand command, UUID idempotencyKey) {
        return executeMutation(
                command.userId(),
                () -> updateInMutationLane(command, idempotencyKey)
        );
    }

    private CartResult updateInMutationLane(UpdateCartCommand command, UUID idempotencyKey) {
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
        CartToolCallContext callContext = new CartToolCallContext(idempotencyKey, command.buyerIp());
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
        if (!cartUpdateApplied(request, result.response())) {
            UcpCartToolResult reconciled = merchantCartPluginDispatchService.getCart(
                    target, new GetCartRequest(cart.getRemoteCartId()), session, callContext);
            if (!cartUpdateApplied(request, reconciled.response())) {
                cartBindingMetrics.record(CartException.BindingFailure.IDENTITY_MISMATCH);
                throw CartException.binding(
                        CartException.BindingFailure.IDENTITY_MISMATCH,
                        "Cart provider did not apply the requested cart update");
            }
            result = reconciled;
        }
        return cartResultMapper.from(
                cartPersistenceService.saveSnapshot(
                        cart,
                        command.userId(),
                        target,
                        result,
                        request.giftCardCodes(),
                        resolved.offers(),
                        CartSnapshotPurpose.CART_MUTATION
                ),
                result.response(),
                target.merchantProvider()
        );
    }

    private boolean cartUpdateApplied(UpdateCartRequest request, UcpCartResponse response) {
        if (request.replacementState() != null) {
            return cartReplacementService.proves(response, request.replacementState());
        }
        Set<String> removedLineIds = new HashSet<>(safeList(request.removeLineIds()).stream()
                .filter(CheckoutFulfillmentSupport::hasText)
                .map(String::trim)
                .toList());
        if (removedLineIds.isEmpty()) {
            return true;
        }
        if (response == null || response.cart() == null || response.cart().lines() == null
                || response.cart().lines().stream().anyMatch(line -> line == null || !hasText(line.id()))) {
            return false;
        }
        return response.cart().lines().stream().noneMatch(line -> removedLineIds.contains(line.id().trim()));
    }

    private boolean providerBound(Cart cart) {
        return hasText(cart.getRoutingScopeKey()) && !cart.getRoutingScopeKey().startsWith("LEGACY:");
    }

    public CheckoutResult checkout(@NotNull @Valid GetCheckoutQuery query) {
        return checkout(query, null);
    }

    public CheckoutResult checkout(@NotNull @Valid GetCheckoutQuery query, UUID idempotencyKey) {
        return executeMutation(
                query.userId(),
                () -> checkoutInMutationLane(query, idempotencyKey)
        );
    }

    private CheckoutResult checkoutInMutationLane(GetCheckoutQuery query, UUID idempotencyKey) {
        Cart cart = findCart(query.cartId(), query.userId());
        CartRoutingTarget target = checkoutRoutingTarget(cart);
        MerchantCartProvider provider = target.merchantProvider();
        if (!query.refresh() && hasText(cart.getCheckoutId())) {
            return withSavedCheckoutDetails(
                    checkoutResultMapper.from(cart, provider),
                    query.userId()
            );
        }
        UcpSession session = session(cart);
        CheckoutToolCallContext callContext = new CheckoutToolCallContext(
                idempotencyKey,
                true,
                query.buyerIp()
        );
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
        return withSavedCheckoutDetails(
                checkoutResultMapper.from(refreshedCart, result.response(), provider),
                query.userId()
        );
    }

    public CheckoutResult getCheckout(@NotNull @Valid GetCheckoutQuery query) {
        if (query.refresh()) {
            return executeMutation(query.userId(), () -> getCheckoutInternal(query));
        }
        return getCheckoutInternal(query);
    }

    private CheckoutResult getCheckoutInternal(GetCheckoutQuery query) {
        Cart cart = findCart(query.cartId(), query.userId());
        if (!hasText(cart.getCheckoutId())) {
            throw CartException.notFound("Checkout not found for cart: " + query.cartId());
        }
        CartRoutingTarget target = checkoutRoutingTarget(cart);
        MerchantCartProvider provider = target.merchantProvider();
        if (!query.refresh()) {
            return withSavedCheckoutDetails(
                    checkoutResultMapper.from(cart, provider),
                    query.userId()
            );
        }
        UcpCheckoutToolResult result = merchantCheckoutPluginDispatchService.getCheckout(
                target,
                new GetCheckoutRequest(cart.getCheckoutId()),
                session(cart),
                CheckoutToolCallContext.forBuyer(query.buyerIp())
        );
        Cart refreshedCart = cartPersistenceService.saveCheckoutHandoff(
                cart.getId(), query.userId(), cart.getCheckoutGeneration(), result);
        return withSavedCheckoutDetails(
                checkoutResultMapper.from(refreshedCart, result.response(), provider),
                query.userId()
        );
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
        return updateCheckout(command, null);
    }

    public CheckoutResult updateCheckout(
            @NotNull @Valid UpdateCheckoutCommand command,
            UUID idempotencyKey
    ) {
        return executeMutation(
                command.userId(),
                () -> updateCheckoutInMutationLane(command, idempotencyKey)
        );
    }

    private CheckoutResult updateCheckoutInMutationLane(
            UpdateCheckoutCommand command,
            UUID idempotencyKey
    ) {
        Cart cart = findCart(command.cartId(), command.userId());
        if (!hasText(cart.getCheckoutId())) {
            throw new CartException("Checkout session is required before updating checkout");
        }
        CartRoutingTarget target = checkoutRoutingTarget(cart);
        MerchantCartProvider provider = target.merchantProvider();
        UcpSession session = session(cart);
        CheckoutToolCallContext callContext = new CheckoutToolCallContext(
                idempotencyKey,
                true,
                command.buyerIp()
        );
        UcpCheckoutToolResult currentCheckout = merchantCheckoutPluginDispatchService.getCheckout(
                target,
                new GetCheckoutRequest(cart.getCheckoutId()),
                session,
                callContext
        );
        List<UpdateCheckoutRequest.LineItem> lineItems =
                updateLineItems(cart, currentCheckout.response(), checkoutResultMapper);
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
        if (!hasCheckoutDetailsValidationProblems(result.response())) {
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
        UserCheckoutDetailsResult savedCheckoutDetails = hasCheckoutDetailsValidationProblems(result.response())
                ? savedCheckoutDetails(command.userId()).orElse(null)
                : userCheckoutDetailsService.save(saveCheckoutDetailsCommand(command));
        return checkoutResultMapper.from(refreshedCart, result.response(), provider)
                .withSavedCheckoutDetails(savedCheckoutDetails);
    }

    private CheckoutResult withSavedCheckoutDetails(CheckoutResult result, UUID userId) {
        return result.withSavedCheckoutDetails(savedCheckoutDetails(userId).orElse(null));
    }

    private Optional<UserCheckoutDetailsResult> savedCheckoutDetails(UUID userId) {
        return userCheckoutDetailsService.get(new GetUserCheckoutDetailsQuery(userId));
    }

    private SaveUserCheckoutDetailsCommand saveCheckoutDetailsCommand(UpdateCheckoutCommand command) {
        return new SaveUserCheckoutDetailsCommand(
                command.userId(),
                command.buyer().email(),
                command.buyer().firstName(),
                command.buyer().lastName(),
                command.buyer().phoneNumber(),
                command.shippingAddress().streetAddress(),
                command.shippingAddress().extendedAddress(),
                command.shippingAddress().addressLocality(),
                command.shippingAddress().addressRegion(),
                command.shippingAddress().postalCode(),
                command.shippingAddress().addressCountry()
        );
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
        return executeMutation(
                command.userId(),
                () -> completeCheckoutInMutationLane(command)
        );
    }

    private CheckoutCompletionResult completeCheckoutInMutationLane(CompleteCheckoutCommand command) {
        Cart cart = findCart(command.cartId(), command.userId());
        CartRoutingTarget target = checkoutRoutingTarget(cart);
        MerchantCartProvider provider = target.merchantProvider();
        Cart checkoutCart = ensureHandoffWhenDirectCompletionUnavailable(
                cart,
                command.userId(),
                target,
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
        return completionResult(checkoutCart, provider, result);
    }

    public CheckoutConsentResult recordCheckoutConsent(@NotNull @Valid CreateCheckoutConsentCommand command) {
        return executeMutation(
                command.userId(),
                () -> recordCheckoutConsentInMutationLane(command)
        );
    }

    private CheckoutConsentResult recordCheckoutConsentInMutationLane(CreateCheckoutConsentCommand command) {
        Cart cart = findCart(command.cartId(), command.userId());
        return cartCheckoutConsentService.recordConsent(cart, command);
    }

    public CheckoutCompletionResult cancelCheckout(@NotNull @Valid CancelCheckoutCommand command) {
        return executeMutation(
                command.userId(),
                () -> cancelCheckoutInMutationLane(command)
        );
    }

    private CheckoutCompletionResult cancelCheckoutInMutationLane(CancelCheckoutCommand command) {
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
                refreshed.getCheckoutId(),
                null,
                refreshed.getContinueUrl(),
                checkoutMessages(refreshed, target.merchantProvider(), result.response()),
                false
        );
    }

    private List<String> checkoutMessages(
            Cart cart,
            MerchantCartProvider provider,
            UcpCheckoutResponse response
    ) {
        if (response == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>(Stream.concat(response.messages().stream(),
                        response.resolvedCheckout() == null ? Stream.empty()
                                : response.resolvedCheckout().messages().stream())
                .map(UcpCheckoutResponse.CheckoutMessage::message)
                .filter(CheckoutFulfillmentSupport::hasText)
                .map(message -> sanitizeBuyerText(cart, provider, message))
                .toList());
        response.errors().stream().map(UcpCheckoutResponse.CheckoutError::message)
                .filter(CheckoutFulfillmentSupport::hasText)
                .map(message -> sanitizeBuyerText(cart, provider, message))
                .forEach(values::add);
        return List.copyOf(values);
    }

    public void cancel(@NotNull @Valid CancelCartCommand command) {
        executeMutation(
                command.userId(),
                () -> {
                    cancelInMutationLane(command);
                    return null;
                }
        );
    }

    private void cancelInMutationLane(CancelCartCommand command) {
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

    private <T> T executeMutation(UUID userId, Supplier<T> mutation) {
        try {
            return userMutationExecutionLane.execute(userId, mutation);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw CartException.rejected("The cart mutation was interrupted before it started.");
        }
    }

    private CreateCartRequest createCartRequest(
            CreateCartCommand command, ResolvedItems resolved, UserCommerceContextResult commerceContext) {
        return new CreateCartRequest(
                resolved.items().entrySet().stream()
                        .map(entry -> cartAddItem(resolved.byKey().get(entry.getKey()), entry.getValue()))
                        .toList(),
                cartBuyer(command.buyerIdentity()),
                cartContext(cartBuyerContextService.buyerContext(commerceContext), command.buyerIdentity()),
                cartDeliveryAddresses(command.deliveryAddressesToAdd()),
                cartDeliveryAddresses(command.deliveryAddressesToReplace()),
                cartDeliveryOptions(command.selectedDeliveryOptions()),
                normalizeCodes(command.discountCodes()),
                normalizeCodes(command.giftCardCodes()),
                command.note()
        );
    }

    private Cart ensureHandoffWhenDirectCompletionUnavailable(
            Cart cart,
            UUID userId,
            CartRoutingTarget target,
            boolean ap2SecurityLock,
            String buyerIp
    ) {
        MerchantCartProvider provider = target.merchantProvider();
        if (provider.executionPolicy().isAvailable(CommerceOperation.DIRECT_CHECKOUT_COMPLETION)
                || ap2SecurityLock
                || hasText(handoffUrl(cart))) {
            return cart;
        }
        UcpCheckoutToolResult result = merchantCheckoutPluginDispatchService.createCheckout(
                target,
                createCheckoutRequest(cart),
                session(cart),
                CheckoutToolCallContext.forBuyer(buyerIp)
        );
        return cartPersistenceService.saveCheckoutHandoff(
                cart.getId(), userId, cart.getCheckoutGeneration(), result);
    }

    private CheckoutCompletionResult completionResult(
            Cart cart,
            MerchantCartProvider provider,
            NativeCheckoutResult result
    ) {
        return new CheckoutCompletionResult(
                cart.getId(),
                cart.getRemoteCartId(),
                result.status(),
                result.checkoutId(),
                result.orderRef(),
                result.continueUrl(),
                result.messages().stream()
                        .map(message -> sanitizeBuyerText(cart, provider, message))
                        .toList(),
                result.nativeAttempted()
        );
    }

    private String sanitizeBuyerText(
            Cart cart,
            MerchantCartProvider provider,
            String value
    ) {
        String providerSafe = MerchantBuyerTextSanitizer.sanitize(value, provider);
        return MerchantBuyerTextSanitizer.sanitize(
                providerSafe,
                cart.getMerchantDomain(),
                cart.getRoutingDomain(),
                cart.getEndpoint()
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
                        null, cart.getMerchantDomain(), routingDomain(cart), cart.getEndpoint(), null,
                        List.of(), MerchantExecutionPolicy.unavailable(), null, Set.of()));
        return checkoutPolicyRequired
                ? selectedOfferCartRoutingService.resolvePersistedExternalForCheckout(persistedTarget)
                : selectedOfferCartRoutingService.resolvePersistedExternal(persistedTarget);
    }

    private String routingDomain(Cart cart) {
        return hasText(cart.getRoutingDomain()) ? cart.getRoutingDomain() : cart.getMerchantDomain();
    }

    private CartResult storedCartResult(Cart cart) {
        return cartResultMapper.storedBuyerTextMayContainTransport(cart)
                ? cartResultMapper.from(cart, storedPresentationProvider(cart))
                : cartResultMapper.from(cart);
    }

    private MerchantCartProvider storedPresentationProvider(Cart cart) {
        Optional<MerchantCartProvider> provider = cart.getMerchantId() == null
                ? Optional.empty()
                : merchantCartProviderLookupService.findById(cart.getMerchantId());
        if (provider.isEmpty()
                && MerchantIntegrationProvider.SHOPIFY.name().equals(cart.getProvider())
                && hasText(cart.getExternalMerchantId())) {
            provider = merchantCartProviderLookupService.findActiveByShopifyShopId(
                    cart.getExternalMerchantId()
            );
        }
        if (provider.isEmpty() && hasText(cart.getMerchantDomain())) {
            provider = merchantCartProviderLookupService.findActiveByCanonicalDomain(
                    cart.getMerchantDomain()
            );
        }
        return provider.orElseGet(() -> new MerchantCartProvider(
                cart.getMerchantId(),
                cart.getMerchantDomain(),
                routingDomain(cart),
                cart.getEndpoint(),
                null,
                List.of(),
                MerchantExecutionPolicy.unavailable(),
                null,
                Set.of()
        ));
    }

    private record ResolvedItems(
            Map<String, Integer> items,
            Map<String, ResolvedSelectedOffer> byKey
    ) {
        private List<ResolvedSelectedOffer> offers() {
            return List.copyOf(byKey.values());
        }
    }

    private record MutableOfferPartition(
            CartRoutingTarget target,
            List<CartOfferPartitionResult.Item> items
    ) {
        private MutableOfferPartition(CartRoutingTarget target) {
            this(target, new ArrayList<>());
        }
    }

    private record SelectedOfferQuantity(String offerKey, Integer quantity) {
    }

    private void validateUnambiguousConfigurations(
            List<CartLine> existingLines, List<ResolvedSelectedOffer> addedOffers) {
        Map<String, Set<String>> offerKeysByVariant = new LinkedHashMap<>();
        safeList(existingLines).stream()
                .filter(line -> line.getOfferKey() != null && hasText(line.getExternalVariantId()))
                .forEach(line -> offerKeysByVariant
                        .computeIfAbsent(line.getExternalVariantId(), ignored -> new HashSet<>())
                        .add(line.getOfferKey()));
        safeList(addedOffers).forEach(offer -> {
            String variant = offer.identity().externalVariantIdentity() == null
                    ? null : offer.identity().externalVariantIdentity().value();
            if (hasText(variant)) {
                offerKeysByVariant.computeIfAbsent(variant, ignored -> new HashSet<>())
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

}
