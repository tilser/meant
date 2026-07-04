package com.meant.api.module.cart.service;

import static com.meant.api.common.util.CollectionUtils.safeList;

import com.meant.api.module.cart.entity.Cart;
import com.meant.api.module.cart.entity.CartLine;
import com.meant.api.module.cart.exception.CartException;
import com.meant.api.module.cart.service.command.CancelCheckoutCommand;
import com.meant.api.module.cart.service.command.CancelCartCommand;
import com.meant.api.module.cart.service.command.CompleteCheckoutCommand;
import com.meant.api.module.cart.service.command.CreateCartCommand;
import com.meant.api.module.cart.service.command.UpdateCartCommand;
import com.meant.api.module.cart.service.dto.CartResult;
import com.meant.api.module.cart.service.dto.CheckoutCompletionResult;
import com.meant.api.module.cart.service.dto.CheckoutResult;
import com.meant.api.module.cart.service.query.GetCartQuery;
import com.meant.api.module.cart.service.query.GetCheckoutQuery;
import com.meant.api.module.merchant.service.MerchantCartProviderLookupService;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.module.user.service.UserInventoryService;
import com.meant.api.module.user.service.command.ImportPurchasedInventoryItemsCommand;
import com.meant.api.plugin.cart.common.dto.CartAddItem;
import com.meant.api.plugin.cart.common.dto.CartUpdateItem;
import com.meant.api.plugin.cart.common.dto.UcpCartToolResult;
import com.meant.api.plugin.cart.common.service.MerchantCartPluginDispatchService;
import com.meant.api.plugin.cart.create.dto.CreateCartRequest;
import com.meant.api.plugin.cart.get.dto.GetCartRequest;
import com.meant.api.plugin.cart.update.dto.UpdateCartRequest;
import com.meant.api.plugin.cart.cancel.dto.CancelCartRequest;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutToolResult;
import com.meant.api.plugin.checkout.common.service.MerchantCheckoutPluginDispatchService;
import com.meant.api.plugin.checkout.common.service.NativeCheckoutCompletionService;
import com.meant.api.plugin.checkout.common.service.command.NativeCheckoutCancellationCommand;
import com.meant.api.plugin.checkout.common.service.command.NativeCheckoutCompletionCommand;
import com.meant.api.plugin.checkout.common.service.dto.NativeCheckoutResult;
import com.meant.api.plugin.checkout.common.service.dto.NativeCheckoutStatus;
import com.meant.api.plugin.checkout.create.dto.CreateCheckoutRequest;
import com.meant.api.plugin.support.UcpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class CartService {

    private final MerchantCartProviderLookupService merchantCartProviderLookupService;
    private final CartPersistenceService cartPersistenceService;
    private final MerchantCartPluginDispatchService merchantCartPluginDispatchService;
    private final MerchantCheckoutPluginDispatchService merchantCheckoutPluginDispatchService;
    private final NativeCheckoutCompletionService nativeCheckoutCompletionService;
    private final UserInventoryService userInventoryService;
    private final CartResultMapper cartResultMapper;

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
        if (hasText(handoffUrl(cart))) {
            importCartInventory(cart);
            return new CheckoutResult(
                    cart.getId(),
                    cart.getRemoteCartId(),
                    cart.getCheckoutUrl(),
                    cart.getContinueUrl(),
                    provider.nativeCheckoutEnabled()
            );
        }
        UcpSession session = session(cart);
        UcpCheckoutToolResult result = merchantCheckoutPluginDispatchService.createCheckout(
                provider,
                new CreateCheckoutRequest(
                        cart.getRemoteCartId(),
                        cart.getLines().stream()
                                .map(line -> new CreateCheckoutRequest.LineItem(
                                        line.getProductVariantId(),
                                        line.getQuantity()
                                ))
                                .toList()
                ),
                session
        );
        Cart refreshedCart = cartPersistenceService.saveCheckoutHandoff(cart, query.userId(), result);
        importCartInventory(refreshedCart);
        return new CheckoutResult(
                refreshedCart.getId(),
                refreshedCart.getRemoteCartId(),
                refreshedCart.getCheckoutUrl(),
                refreshedCart.getContinueUrl(),
                provider.nativeCheckoutEnabled()
        );
    }

    public CheckoutCompletionResult completeCheckout(@NotNull @Valid CompleteCheckoutCommand command) {
        Cart cart = findCart(command.cartId(), command.userId());
        MerchantCartProvider provider = findProvider(cart.getMerchantId(), cart.getMerchantDomain());
        Cart checkoutCart = ensureHandoffWhenNativeDisabled(cart, command.userId(), provider, command.ap2SecurityLock());
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
                safeList(command.deliveryAddressesToAdd()),
                safeList(command.deliveryAddressesToReplace()),
                safeList(command.selectedDeliveryOptions()),
                normalizeCodes(command.discountCodes()),
                normalizeCodes(command.giftCardCodes()),
                command.note()
        );
    }

    private Cart ensureHandoffWhenNativeDisabled(
            Cart cart,
            UUID userId,
            MerchantCartProvider provider,
            boolean ap2SecurityLock
    ) {
        if (provider.nativeCheckoutEnabled() || ap2SecurityLock || hasText(handoffUrl(cart))) {
            return cart;
        }
        UcpCheckoutToolResult result = merchantCheckoutPluginDispatchService.createCheckout(
                provider,
                new CreateCheckoutRequest(cart.getRemoteCartId()),
                session(cart)
        );
        return cartPersistenceService.saveCheckoutHandoff(cart, userId, result);
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
                command.signals()
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
        safeList(command.removeCartLineIds()).forEach(cartLineId -> {
            CartLine line = linesByLocalId.get(cartLineId);
            if (line == null || line.getRemoteCartLineId() == null || line.getRemoteCartLineId().isBlank()) {
                throw CartException.notFound("Cart line not found: " + cartLineId);
            }
            items.put(line.getRemoteCartLineId(), new CartUpdateItem(
                    line.getRemoteCartLineId(),
                    line.getProductVariantId(),
                    0
            ));
        });
        safeList(command.removeRemoteCartLineIds()).stream()
                .filter(value -> value != null && !value.isBlank())
                .forEach(remoteCartLineId -> {
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
