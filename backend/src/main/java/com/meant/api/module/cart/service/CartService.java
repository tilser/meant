package com.meant.api.module.cart.service;

import static com.meant.api.common.util.CollectionUtils.safeList;
import static com.meant.api.common.util.CollectionUtils.safeNonNullList;

import com.meant.api.module.cart.entity.Cart;
import com.meant.api.module.cart.entity.CartLine;
import com.meant.api.module.cart.exception.CartException;
import com.meant.api.module.cart.service.command.CancelCheckoutCommand;
import com.meant.api.module.cart.service.command.CancelCartCommand;
import com.meant.api.module.cart.service.command.CompleteCheckoutCommand;
import com.meant.api.module.cart.service.command.CreateCartCommand;
import com.meant.api.module.cart.service.command.CreateCheckoutConsentCommand;
import com.meant.api.module.cart.service.command.UpdateCartCommand;
import com.meant.api.module.cart.service.command.UpdateCheckoutCommand;
import com.meant.api.module.cart.service.dto.CartResult;
import com.meant.api.module.cart.service.dto.CheckoutConsentResult;
import com.meant.api.module.cart.service.dto.CheckoutCompletionResult;
import com.meant.api.module.cart.service.dto.CheckoutResult;
import com.meant.api.module.cart.service.query.GetCartQuery;
import com.meant.api.module.cart.service.query.GetCheckoutQuery;
import com.meant.api.module.merchant.constant.CommerceOperation;
import com.meant.api.module.merchant.service.MerchantCartProviderLookupService;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.module.user.service.UserInventoryService;
import com.meant.api.module.user.service.command.ImportPurchasedInventoryItemsCommand;
import com.meant.api.plugin.cart.common.dto.CartAddItem;
import com.meant.api.plugin.cart.common.dto.CartUpdateItem;
import com.meant.api.plugin.cart.common.dto.UcpCartToolResult;
import com.meant.api.module.cart.service.MerchantCartPluginDispatchService;
import com.meant.api.plugin.cart.create.dto.CreateCartRequest;
import com.meant.api.plugin.cart.get.dto.GetCartRequest;
import com.meant.api.plugin.cart.update.dto.UpdateCartRequest;
import com.meant.api.plugin.cart.cancel.dto.CancelCartRequest;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutToolResult;
import com.meant.api.module.checkout.service.MerchantCheckoutPluginDispatchService;
import com.meant.api.module.checkout.service.NativeCheckoutCompletionService;
import com.meant.api.module.checkout.service.command.NativeCheckoutCancellationCommand;
import com.meant.api.module.checkout.service.command.NativeCheckoutCompletionCommand;
import com.meant.api.module.checkout.service.dto.NativeCheckoutResult;
import com.meant.api.module.checkout.service.dto.NativeCheckoutStatus;
import com.meant.api.plugin.checkout.complete.dto.CheckoutSignals;
import com.meant.api.plugin.checkout.create.dto.CreateCheckoutRequest;
import com.meant.api.plugin.checkout.get.dto.GetCheckoutRequest;
import com.meant.api.plugin.checkout.update.dto.UpdateCheckoutRequest;
import com.meant.api.plugin.support.UcpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class CartService {

    private final MerchantCartProviderLookupService merchantCartProviderLookupService;
    private final CartBuyerContextService cartBuyerContextService;
    private final CartPersistenceService cartPersistenceService;
    private final MerchantCartPluginDispatchService merchantCartPluginDispatchService;
    private final MerchantCheckoutPluginDispatchService merchantCheckoutPluginDispatchService;
    private final NativeCheckoutCompletionService nativeCheckoutCompletionService;
    private final UserInventoryService userInventoryService;
    private final CartResultMapper cartResultMapper;
    private final CheckoutResultMapper checkoutResultMapper;
    private final CartCheckoutConsentService cartCheckoutConsentService;

    public CartResult create(@NotNull @Valid CreateCartCommand command) {
        MerchantCartProvider provider = findProvider(command.merchantId(), command.merchantDomain());
        CreateCartRequest request = createCartRequest(command);
        UcpSession session = UcpSession.start();
        UcpCartToolResult result = merchantCartPluginDispatchService.createCart(provider, request, session);
        return cartResultMapper.from(cartPersistenceService.saveSnapshot(
                (UUID) null,
                command.userId(),
                provider,
                result,
                request.giftCardCodes()
        ));
    }

    public CartResult get(@NotNull @Valid GetCartQuery query) {
        Cart cart = findCart(query.cartId(), query.userId());
        if (!query.refresh()) {
            return cartResultMapper.from(cart);
        }
        MerchantCartProvider provider = findProvider(cart.getMerchantId(), cart.getMerchantDomain());
        UcpSession session = session(cart);
        UcpCartToolResult result = merchantCartPluginDispatchService.getCart(
                provider,
                new GetCartRequest(cart.getRemoteCartId()),
                session
        );
        return cartResultMapper.from(cartPersistenceService.saveSnapshot(cart, query.userId(), provider, result));
    }

    public CartResult update(@NotNull @Valid UpdateCartCommand command) {
        Cart cart = findCart(command.cartId(), command.userId());
        MerchantCartProvider provider = findProvider(cart.getMerchantId(), cart.getMerchantDomain());
        UpdateCartRequest request = updateCartRequest(cart, command);
        UcpSession session = session(cart);
        UcpCartToolResult result = merchantCartPluginDispatchService.updateCart(provider, request, session);
        return cartResultMapper.from(cartPersistenceService.saveSnapshot(
                cart,
                command.userId(),
                provider,
                result,
                request.giftCardCodes()
        ));
    }

    public CheckoutResult checkout(@NotNull @Valid GetCheckoutQuery query) {
        Cart cart = findCart(query.cartId(), query.userId());
        MerchantCartProvider provider = findProvider(cart.getMerchantId(), cart.getMerchantDomain());
        if (!query.refresh() && hasText(cart.getCheckoutId())) {
            importCartInventory(cart);
            return checkoutResultMapper.from(cart, provider.executionPolicy());
        }
        UcpSession session = session(cart);
        Cart checkoutCart = hasText(cart.getCheckoutId())
                ? cart
                : refreshEmptyCartBeforeCheckout(cart, query.userId(), provider, session);
        UcpCheckoutToolResult result = query.refresh() && hasText(cart.getCheckoutId())
                ? merchantCheckoutPluginDispatchService.getCheckout(
                        provider,
                        new GetCheckoutRequest(cart.getCheckoutId()),
                        session
                )
                : merchantCheckoutPluginDispatchService.createCheckout(
                        provider,
                        createCheckoutRequest(checkoutCart),
                        session
                );
        Cart refreshedCart = cartPersistenceService.saveCheckoutHandoff(checkoutCart, query.userId(), result);
        importCartInventory(refreshedCart);
        return checkoutResultMapper.from(refreshedCart, provider.executionPolicy());
    }

    private Cart refreshEmptyCartBeforeCheckout(
            Cart cart,
            UUID userId,
            MerchantCartProvider provider,
            UcpSession session
    ) {
        if (!cart.getLines().isEmpty()) {
            return cart;
        }
        UcpCartToolResult result = merchantCartPluginDispatchService.getCart(
                provider,
                new GetCartRequest(cart.getRemoteCartId()),
                session
        );
        return cartPersistenceService.saveSnapshot(cart, userId, provider, result);
    }

    public CheckoutResult updateCheckout(@NotNull @Valid UpdateCheckoutCommand command) {
        Cart cart = findCart(command.cartId(), command.userId());
        if (!hasText(cart.getCheckoutId())) {
            throw new CartException("Checkout session is required before updating checkout");
        }
        MerchantCartProvider provider = findProvider(cart.getMerchantId(), cart.getMerchantDomain());
        List<UpdateCheckoutRequest.LineItem> lineItems = updateCheckoutLineItems(cart);
        Map<String, Object> buyer = buyer(command.buyer());
        Map<String, Object> shippingAddress = postalAddress(command.buyer(), command.shippingAddress());
        Map<String, Object> context = cartBuyerContextService.buyerContext(command.userId());
        List<String> discountCodes = normalizeCodes(command.discountCodes());
        UcpSession session = session(cart);
        UcpCheckoutToolResult result = merchantCheckoutPluginDispatchService.updateCheckout(
                provider,
                new UpdateCheckoutRequest(
                        cart.getCheckoutId(),
                        lineItems,
                        buyer,
                        null,
                        command.buyer().email(),
                        cart.getCurrency(),
                        context,
                        discountCodes,
                        fulfillment(command.buyer(), command.shippingAddress(), lineItems)
                ),
                session
        );
        // A field-validation rejection (e.g. buyer_identity_email_is_invalid) means the merchant
        // discarded this update. Refreshing the checkout here would replace the validation message
        // with the stale pre-update state (e.g. "address required"), hiding the real problem.
        if (!hasFieldValidationMessages(result.response())) {
            if (fulfillmentOptionsMissing(result.response())) {
                result = merchantCheckoutPluginDispatchService.getCheckout(
                        provider,
                        new GetCheckoutRequest(cart.getCheckoutId()),
                        session
                );
            }
            Map<String, Object> defaultFulfillmentSelection =
                    defaultFulfillmentSelection(result.response(), shippingAddress);
            if (!defaultFulfillmentSelection.isEmpty()) {
                result = merchantCheckoutPluginDispatchService.updateCheckout(
                        provider,
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
                        session
                );
            }
        }
        Cart refreshedCart = cartPersistenceService.saveCheckoutHandoff(cart, command.userId(), result);
        importCartInventory(refreshedCart);
        return checkoutResultMapper.from(refreshedCart, provider.executionPolicy());
    }

    private List<UpdateCheckoutRequest.LineItem> updateCheckoutLineItems(Cart cart) {
        List<UcpCheckoutResponse.CheckoutLineItem> checkoutLines = new ArrayList<>(checkoutLineItems(cart));
        return cart.getLines().stream()
                .map(line -> {
                    UcpCheckoutResponse.CheckoutLineItem checkoutLine = takeMatchingCheckoutLine(checkoutLines, line);
                    return new UpdateCheckoutRequest.LineItem(
                            checkoutLineId(checkoutLine),
                            line.getProductVariantId(),
                            line.getQuantity()
                    );
                })
                .toList();
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
        if (matchingIndex < 0 && checkoutLines.size() == 1) {
            matchingIndex = 0;
        }
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
                Map.of(),
                null,
                cart.getCurrency(),
                cartBuyerContextService.buyerContext(cart.getUserId()),
                List.of(),
                Map.of()
        );
    }

    public CheckoutCompletionResult completeCheckout(@NotNull @Valid CompleteCheckoutCommand command) {
        Cart cart = findCart(command.cartId(), command.userId());
        MerchantCartProvider provider = findProvider(cart.getMerchantId(), cart.getMerchantDomain());
        Cart checkoutCart = ensureHandoffWhenDirectCompletionUnavailable(
                cart,
                command.userId(),
                provider,
                command.ap2SecurityLock()
        );
        NativeCheckoutResult result = nativeCheckoutCompletionService.complete(
                provider,
                nativeCompletionCommand(command),
                session(checkoutCart)
        );
        if (result.status() == NativeCheckoutStatus.COMPLETED) {
            importCartInventory(checkoutCart);
        }
        return completionResult(checkoutCart, result);
    }

    public CheckoutConsentResult recordCheckoutConsent(@NotNull @Valid CreateCheckoutConsentCommand command) {
        Cart cart = findCart(command.cartId(), command.userId());
        return cartCheckoutConsentService.recordConsent(cart, command);
    }

    public CheckoutCompletionResult cancelCheckout(@NotNull @Valid CancelCheckoutCommand command) {
        Cart cart = findCart(command.cartId(), command.userId());
        MerchantCartProvider provider = findProvider(cart.getMerchantId(), cart.getMerchantDomain());
        NativeCheckoutResult result = nativeCheckoutCompletionService.cancel(
                provider,
                new NativeCheckoutCancellationCommand(
                        command.cartId(),
                        command.checkoutId(),
                        command.reason(),
                        command.ap2SecurityLock()
                ),
                session(cart)
        );
        return completionResult(cart, result);
    }

    public void cancel(@NotNull @Valid CancelCartCommand command) {
        Cart cart = findCart(command.cartId(), command.userId());
        MerchantCartProvider provider = findProvider(cart.getMerchantId(), cart.getMerchantDomain());
        merchantCartPluginDispatchService.cancelCart(
                provider,
                new CancelCartRequest(cart.getRemoteCartId()),
                session(cart)
        );
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

    private CreateCartRequest createCartRequest(CreateCartCommand command) {
        return new CreateCartRequest(
                safeList(command.addItems()).stream()
                        .map(item -> new CartAddItem(item.productVariantId(), item.quantity()))
                        .toList(),
                command.buyerIdentity(),
                cartBuyerContextService.buyerContext(command.userId()),
                safeList(command.deliveryAddressesToAdd()),
                safeList(command.deliveryAddressesToReplace()),
                safeList(command.selectedDeliveryOptions()),
                normalizeCodes(command.discountCodes()),
                normalizeCodes(command.giftCardCodes()),
                command.note()
        );
    }

    private Cart ensureHandoffWhenDirectCompletionUnavailable(
            Cart cart,
            UUID userId,
            MerchantCartProvider provider,
            boolean ap2SecurityLock
    ) {
        if (provider.executionPolicy().isAvailable(CommerceOperation.DIRECT_CHECKOUT_COMPLETION)
                || ap2SecurityLock
                || hasText(handoffUrl(cart))) {
            return cart;
        }
        UcpCheckoutToolResult result = merchantCheckoutPluginDispatchService.createCheckout(
                provider,
                createCheckoutRequest(cart),
                session(cart)
        );
        return cartPersistenceService.saveCheckoutHandoff(cart, userId, result);
    }

    private Map<String, Object> buyer(UpdateCheckoutCommand.Buyer buyer) {
        Map<String, Object> values = new LinkedHashMap<>();
        putIfHasText(values, "email", buyer.email());
        putIfHasText(values, "first_name", buyer.firstName());
        putIfHasText(values, "last_name", buyer.lastName());
        putIfHasText(values, "phone_number", buyer.phoneNumber());
        return values;
    }

    private Map<String, Object> fulfillment(
            UpdateCheckoutCommand.Buyer buyer,
            UpdateCheckoutCommand.PostalAddress address,
            List<UpdateCheckoutRequest.LineItem> lineItems
    ) {
        Map<String, Object> destination = postalAddress(buyer, address);
        List<String> lineItemIds = lineItemIds(lineItems);
        Map<String, Object> method = new LinkedHashMap<>();
        method.put("id", "shipping");
        method.put("type", "shipping");
        putIfNotEmpty(method, "line_item_ids", lineItemIds);
        method.put("selected_destination_id", "shipping");
        method.put("destinations", List.of(destination));
        return Map.of("methods", List.of(method));
    }

    private Map<String, Object> defaultFulfillmentSelection(
            UcpCheckoutResponse response,
            Map<String, Object> destination
    ) {
        UcpCheckoutResponse.Checkout checkout = response == null ? null : response.resolvedCheckout();
        UcpCheckoutResponse.CheckoutFulfillment fulfillment = checkout == null ? null : checkout.fulfillment();
        if (fulfillment == null || fulfillment.methods().isEmpty()) {
            return Map.of();
        }
        List<Map<String, Object>> methods = fulfillment.methods().stream()
                .map(method -> defaultFulfillmentMethodSelection(method, destination))
                .filter(selection -> !selection.isEmpty())
                .toList();
        return methods.isEmpty() ? Map.of() : Map.of("methods", methods);
    }

    private Map<String, Object> defaultFulfillmentMethodSelection(
            UcpCheckoutResponse.CheckoutFulfillmentMethod method,
            Map<String, Object> destination
    ) {
        if (method == null || method.groups().isEmpty()) {
            return Map.of();
        }
        List<Map<String, Object>> groups = method.groups().stream()
                .map(this::defaultFulfillmentGroupSelection)
                .filter(selection -> !selection.isEmpty())
                .toList();
        if (groups.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        putIfHasText(values, "id", firstText(method.id(), method.type()));
        putIfHasText(values, "type", method.type());
        putIfNotEmpty(values, "line_item_ids", method.lineItemIds());
        putIfHasText(values, "selected_destination_id", selectedDestinationId(method));
        // Update checkout uses replacement semantics, so the selection must carry the
        // destination again or the merchant may drop the shipping address.
        if (destination != null && !destination.isEmpty()) {
            values.put("destinations", List.of(destination));
        }
        values.put("groups", groups);
        return values;
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

    private Map<String, Object> defaultFulfillmentGroupSelection(UcpCheckoutResponse.CheckoutFulfillmentGroup group) {
        if (group == null || hasText(group.selectedOptionId())) {
            return Map.of();
        }
        String selectedOptionId = defaultOptionId(group);
        if (!hasText(group.id()) || !hasText(selectedOptionId)) {
            return Map.of();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", group.id());
        putIfNotEmpty(values, "line_item_ids", group.lineItemIds());
        values.put("selected_option_id", selectedOptionId);
        return values;
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

    private Map<String, Object> postalAddress(
            UpdateCheckoutCommand.Buyer buyer,
            UpdateCheckoutCommand.PostalAddress address
    ) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", "shipping");
        putIfHasText(values, "street_address", address.streetAddress());
        putIfHasText(values, "extended_address", address.extendedAddress());
        putIfHasText(values, "address_locality", address.addressLocality());
        putIfHasText(values, "address_region", address.addressRegion());
        putIfHasText(values, "postal_code", address.postalCode());
        putIfHasText(values, "address_country", address.addressCountry());
        putIfHasText(values, "first_name", buyer.firstName());
        putIfHasText(values, "last_name", buyer.lastName());
        putIfHasText(values, "phone_number", buyer.phoneNumber());
        return values;
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

    private void putIfHasText(Map<String, Object> values, String key, String value) {
        if (hasText(value)) {
            values.put(key, value.trim());
        }
    }

    private void putIfNotEmpty(Map<String, Object> values, String key, List<String> list) {
        List<String> normalized = safeList(list).stream()
                .filter(this::hasText)
                .map(String::trim)
                .toList();
        if (!normalized.isEmpty()) {
            values.put(key, normalized);
        }
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
                checkoutSignals(command.signals())
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

    private UpdateCartRequest updateCartRequest(Cart cart, UpdateCartCommand command) {
        Map<UUID, CartLine> linesByLocalId = new HashMap<>();
        Map<String, CartLine> linesByRemoteId = new HashMap<>();
        cart.getLines().forEach(line -> {
            linesByLocalId.put(line.getId(), line);
            linesByRemoteId.put(line.getRemoteCartLineId(), line);
        });
        Map<UUID, String> remoteLineIdsByLocalId = remoteLineIds(linesByLocalId);
        List<CartUpdateItem> removeItems = removeItems(command, linesByLocalId, linesByRemoteId);
        return new UpdateCartRequest(
                cart.getRemoteCartId(),
                safeList(command.addItems()).stream()
                        .map(item -> new CartAddItem(item.productVariantId(), item.quantity()))
                        .toList(),
                safeList(command.updateItems()).stream()
                        .map(item -> cartUpdateItem(item, linesByLocalId, linesByRemoteId, remoteLineIdsByLocalId))
                        .toList(),
                removeLineIds(removeItems),
                removeItems,
                command.buyerIdentity(),
                cartBuyerContextService.buyerContext(command.userId()),
                safeList(command.deliveryAddressesToAdd()),
                safeList(command.deliveryAddressesToReplace()),
                safeList(command.selectedDeliveryOptions()),
                normalizeCodes(command.discountCodes()),
                normalizeCodes(command.giftCardCodes()),
                command.note()
        );
    }

    private CartUpdateItem cartUpdateItem(
            UpdateCartCommand.UpdateItem item,
            Map<UUID, CartLine> linesByLocalId,
            Map<String, CartLine> linesByRemoteId,
            Map<UUID, String> remoteLineIdsByLocalId
    ) {
        String remoteCartLineId = remoteCartLineId(item, remoteLineIdsByLocalId);
        CartLine line = item.cartLineId() == null
                ? linesByRemoteId.get(remoteCartLineId)
                : linesByLocalId.get(item.cartLineId());
        return new CartUpdateItem(
                remoteCartLineId,
                line == null ? null : line.getProductVariantId(),
                item.quantity()
        );
    }

    private Map<UUID, String> remoteLineIds(Map<UUID, CartLine> linesByLocalId) {
        Map<UUID, String> remoteLineIds = new HashMap<>();
        linesByLocalId.forEach((id, line) -> remoteLineIds.put(id, line.getRemoteCartLineId()));
        return remoteLineIds;
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

    private String remoteCartLineId(
            UpdateCartCommand.UpdateItem item,
            Map<UUID, String> remoteLineIdsByLocalId
    ) {
        if (item.remoteCartLineId() != null && !item.remoteCartLineId().isBlank()) {
            return item.remoteCartLineId();
        }
        if (item.cartLineId() == null) {
            throw new CartException("cartLineId or remoteCartLineId is required for update item");
        }
        String remoteCartLineId = remoteLineIdsByLocalId.get(item.cartLineId());
        if (remoteCartLineId == null) {
            throw CartException.notFound("Cart line not found: " + item.cartLineId());
        }
        return remoteCartLineId;
    }

    private List<CartUpdateItem> removeItems(
            UpdateCartCommand command,
            Map<UUID, CartLine> linesByLocalId,
            Map<String, CartLine> linesByRemoteId
    ) {
        Map<String, CartUpdateItem> items = new LinkedHashMap<>();
        List<String> requestedRemoteCartLineIds = safeList(command.removeRemoteCartLineIds()).stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
        safeList(command.removeCartLineIds()).forEach(cartLineId -> {
            CartLine line = linesByLocalId.get(cartLineId);
            if (line == null || line.getRemoteCartLineId() == null || line.getRemoteCartLineId().isBlank()) {
                if (requestedRemoteCartLineIds.stream().anyMatch(linesByRemoteId::containsKey)) {
                    return;
                }
                throw CartException.notFound("Cart line not found: " + cartLineId);
            }
            items.put(line.getRemoteCartLineId(), new CartUpdateItem(
                    line.getRemoteCartLineId(),
                    line.getProductVariantId(),
                    0
            ));
        });
        requestedRemoteCartLineIds.forEach(remoteCartLineId -> {
            CartLine line = linesByRemoteId.get(remoteCartLineId);
            if (line == null) {
                throw CartException.notFound("Cart line not found: " + remoteCartLineId);
            }
            items.putIfAbsent(remoteCartLineId, new CartUpdateItem(
                    remoteCartLineId,
                    line.getProductVariantId(),
                    0
            ));
        });
        return List.copyOf(items.values());
    }

    private List<String> removeLineIds(List<CartUpdateItem> removeItems) {
        return safeList(removeItems).stream()
                .map(CartUpdateItem::id)
                .toList();
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

    private void importCartInventory(Cart cart) {
        Instant purchasedAt = inventoryPurchasedAt(cart);
        List<ImportPurchasedInventoryItemsCommand.PurchasedItem> items = cart.getLines().stream()
                .filter(line -> line.getProductVariantId() != null && !line.getProductVariantId().isBlank())
                .map(line -> new ImportPurchasedInventoryItemsCommand.PurchasedItem(
                        cart.getMerchantDomain() + ":" + line.getProductVariantId(),
                        null,
                        productName(line.getProductTitle(), line.getVariantTitle(), line.getProductVariantId()),
                        cart.getMerchantDomain(),
                        null,
                        null,
                        line.getQuantity(),
                        purchasedAt
                ))
                .toList();
        if (!items.isEmpty()) {
            userInventoryService.importPurchasedItems(new ImportPurchasedInventoryItemsCommand(cart.getUserId(), items));
        }
    }

    private Instant inventoryPurchasedAt(Cart cart) {
        if (cart.getRemoteUpdatedAt() != null) {
            return cart.getRemoteUpdatedAt();
        }
        if (cart.getUpdatedAt() != null) {
            return cart.getUpdatedAt();
        }
        return cart.getRefreshedAt();
    }

    private String productName(String productTitle, String variantTitle, String fallback) {
        if (productTitle != null && !productTitle.isBlank()) {
            return productTitle;
        }
        if (variantTitle != null && !variantTitle.isBlank()) {
            return variantTitle;
        }
        return fallback;
    }

}
