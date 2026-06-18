package com.meant.api.module.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.module.cart.constant.CartAppliedCodeType;
import com.meant.api.module.cart.entity.Cart;
import com.meant.api.module.cart.entity.CartAppliedCode;
import com.meant.api.module.cart.entity.CartLine;
import com.meant.api.module.cart.exception.CartException;
import com.meant.api.module.cart.repository.CartRepository;
import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.repository.MerchantRepository;
import com.meant.api.module.merchant.service.MerchantCartProviderLookupService;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.module.cart.service.command.CreateCartCommand;
import com.meant.api.module.cart.service.command.UpdateCartCommand;
import com.meant.api.module.cart.service.dto.CartToolResponse;
import com.meant.api.module.cart.service.dto.CartToolResult;
import com.meant.api.module.cart.service.dto.CartResult;
import com.meant.api.module.cart.service.dto.CheckoutResult;
import com.meant.api.module.cart.service.dto.UpdateCartArguments;
import com.meant.api.module.cart.service.query.GetCartQuery;
import com.meant.api.module.cart.service.query.GetCheckoutQuery;
import com.meant.api.module.user.service.UserInventoryService;
import com.meant.api.module.user.service.command.ImportPurchasedInventoryItemsCommand;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

class CartServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Instant CART_REMOTE_CREATED_AT = Instant.parse("2026-06-16T11:05:00Z");
    private static final Instant CART_REMOTE_UPDATED_AT = Instant.parse("2026-06-16T11:05:01Z");

    private FakeMerchantRepository merchantRepository;
    private FakeCartRepository cartRepository;
    private FakeCartClient cartClient;
    private FakeUserInventoryService userInventoryService;
    private CartService cartService;
    private Merchant merchant;

    @BeforeEach
    void setUp() {
        merchantRepository = new FakeMerchantRepository();
        cartRepository = new FakeCartRepository();
        cartClient = new FakeCartClient();
        userInventoryService = new FakeUserInventoryService();
        CartPersistenceService cartPersistenceService = new CartPersistenceService(
                cartRepository.proxy(),
                new ObjectMapper()
        );
        cartService = new CartService(
                new MerchantCartProviderLookupService(merchantRepository.proxy()),
                cartPersistenceService,
                cartClient,
                userInventoryService
        );
        merchant = merchant();
        merchantRepository.save(merchant);
        cartClient.cartToolResult = cartToolResult();
    }

    @Test
    void createCallsRemoteCartAndSavesSnapshot() {
        CartResult result = cartService.create(new CreateCartCommand(
                USER_ID,
                merchant.getId(),
                null,
                List.of(new CreateCartCommand.AddItem("gid://shopify/ProductVariant/1", 1)),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null
        ));

        assertThat(result.remoteCartId()).isEqualTo("gid://shopify/Cart/1");
        assertThat(result.checkoutUrl()).isEqualTo("https://merchant.example/checkout");
        assertThat(result.lines()).hasSize(1);
        assertThat(result.lines().getFirst().remoteCartLineId()).isEqualTo("gid://shopify/CartLine/1");
        assertThat(cartRepository.saveCount).isEqualTo(1);
        assertThat(cartClient.updateCount).isEqualTo(1);
    }

    @Test
    void createSavesAppliedCodesAndAdjustedTotal() {
        cartClient.cartToolResult = cartToolResultWithAppliedCodes();

        CartResult result = cartService.create(new CreateCartCommand(
                USER_ID,
                merchant.getId(),
                null,
                List.of(new CreateCartCommand.AddItem("gid://shopify/ProductVariant/1", 1)),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of("SAVE5"),
                List.of("CARD1234"),
                null
        ));

        assertThat(result.subtotalAmount()).isEqualTo("14.95");
        assertThat(result.totalAmount()).isEqualTo("7.95");
        assertThat(result.appliedCodes()).hasSize(2);
        assertThat(result.appliedCodes()).extracting("type")
                .containsExactly(CartAppliedCodeType.DISCOUNT, CartAppliedCodeType.GIFT_CARD);
        assertThat(result.appliedCodes()).extracting("code").containsExactly("SAVE5", "CARD1234");
        assertThat(result.appliedCodes()).extracting("amount").containsExactly("5.00", "2.00");
    }

    @Test
    void createCarriesDeliveryGroupsFromRemoteSnapshot() {
        cartClient.cartToolResult = cartToolResult(List.of(cartLine()), 1, List.of(deliveryGroup()));

        CartResult result = cartService.create(new CreateCartCommand(
                USER_ID,
                merchant.getId(),
                null,
                List.of(new CreateCartCommand.AddItem("gid://shopify/ProductVariant/1", 1)),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null
        ));

        assertThat(result.deliveryGroups()).hasSize(1);
        assertThat(result.deliveryGroups().getFirst().id()).isEqualTo("delivery-group-1");
        assertThat(result.deliveryGroups().getFirst().deliveryOptions()).extracting("handle")
                .containsExactly("standard", "express");
        assertThat(result.deliveryGroups().getFirst().selectedDeliveryOption().handle()).isEqualTo("standard");
    }

    @Test
    void getRefreshPreservesKnownFullGiftCardCodeWhenMerchantReturnsLastCharacters() {
        UUID cartId = UUID.randomUUID();
        Cart cart = cart(cartId, "https://merchant.example/stale-checkout");
        cart.replaceAppliedCodes(List.of(CartAppliedCode.builder()
                .type(CartAppliedCodeType.GIFT_CARD)
                .code("CARD1234")
                .label("Gift card")
                .displayOrder(0)
                .build()));
        cartRepository.save(cart);
        cartClient.cartToolResult = cartToolResultWithAppliedCodes();

        CartResult result = cartService.get(new GetCartQuery(cartId, USER_ID, true));

        assertThat(result.appliedCodes())
                .filteredOn(code -> code.type() == CartAppliedCodeType.GIFT_CARD)
                .extracting("code")
                .containsExactly("CARD1234");
    }

    @Test
    void getWithoutRefreshReturnsDeliveryGroupsFromStoredSnapshot() {
        cartClient.cartToolResult = cartToolResult(List.of(cartLine()), 1, List.of(deliveryGroup()));
        CartResult created = cartService.create(new CreateCartCommand(
                USER_ID,
                merchant.getId(),
                null,
                List.of(new CreateCartCommand.AddItem("gid://shopify/ProductVariant/1", 1)),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null
        ));
        cartClient.getCount = 0;

        CartResult result = cartService.get(new GetCartQuery(created.cartId(), USER_ID, false));

        assertThat(result.deliveryGroups()).hasSize(1);
        assertThat(result.deliveryGroups().getFirst().deliveryOptions()).extracting("cost.amount")
                .containsExactly("5.00", "12.00");
        assertThat(cartClient.getCount).isZero();
    }

    @Test
    void getWithoutRefreshUsesStoredSnapshotOnly() {
        UUID cartId = UUID.randomUUID();
        cartRepository.save(cart(cartId, "https://merchant.example/checkout"));

        CartResult result = cartService.get(new GetCartQuery(cartId, USER_ID, false));

        assertThat(result.checkoutUrl()).isEqualTo("https://merchant.example/checkout");
        assertThat(cartClient.getCount).isZero();
        assertThat(merchantRepository.findByIdCount).isZero();
    }

    @Test
    void getWithRefreshCallsRemoteAndSavesSnapshot() {
        UUID cartId = UUID.randomUUID();
        cartRepository.save(cart(cartId, "https://merchant.example/stale-checkout"));

        CartResult result = cartService.get(new GetCartQuery(cartId, USER_ID, true));

        assertThat(result.checkoutUrl()).isEqualTo("https://merchant.example/checkout");
        assertThat(cartClient.getCount).isEqualTo(1);
        assertThat(cartClient.lastRemoteCartId).isEqualTo("gid://shopify/Cart/1");
        assertThat(cartRepository.saveCount).isEqualTo(1);
    }

    @Test
    void updateMapsLocalCartLineIdsToRemoteCartLineIds() {
        UUID cartId = UUID.randomUUID();
        UUID cartLineId = UUID.randomUUID();
        cartRepository.save(cart(cartId, "https://merchant.example/checkout", cartLineId));

        cartService.update(new UpdateCartCommand(
                cartId,
                USER_ID,
                List.of(),
                List.of(new UpdateCartCommand.UpdateItem(cartLineId, null, 2)),
                List.of(cartLineId),
                List.of("gid://shopify/CartLine/1"),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null
        ));

        assertThat(cartClient.lastUpdateArguments.updateItems()).extracting("id")
                .containsExactly("gid://shopify/CartLine/1");
        assertThat(cartClient.lastUpdateArguments.removeLineIds()).containsExactly("gid://shopify/CartLine/1");
    }

    @Test
    void updateOmitsCodesWhenNoCodeChangeIsRequested() {
        UUID cartId = UUID.randomUUID();
        UUID cartLineId = UUID.randomUUID();
        cartRepository.save(cart(cartId, "https://merchant.example/checkout", cartLineId));

        cartService.update(new UpdateCartCommand(
                cartId,
                USER_ID,
                List.of(),
                List.of(new UpdateCartCommand.UpdateItem(cartLineId, null, 2)),
                List.of(),
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null
        ));

        assertThat(cartClient.lastUpdateArguments.discountCodes()).isNull();
        assertThat(cartClient.lastUpdateArguments.giftCardCodes()).isNull();
    }

    @Test
    void updateForwardsSelectedDeliveryOptions() {
        UUID cartId = UUID.randomUUID();
        cartRepository.save(cart(cartId, "https://merchant.example/checkout"));

        cartService.update(new UpdateCartCommand(
                cartId,
                USER_ID,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(Map.of(
                        "delivery_group_id", "delivery-group-1",
                        "delivery_option_handle", "express"
                )),
                List.of(),
                List.of(),
                null
        ));

        assertThat(cartClient.lastUpdateArguments.selectedDeliveryOptions())
                .containsExactly(Map.of(
                        "delivery_group_id", "delivery-group-1",
                        "delivery_option_handle", "express"
                ));
    }

    @Test
    void updateRejectedByMerchantLeavesStoredCartUnchanged() {
        UUID cartId = UUID.randomUUID();
        cartRepository.save(cart(cartId, "https://merchant.example/checkout"));
        cartClient.updateException = CartException.rejected("Discount code EXPIRED was not accepted by the merchant.");

        assertThatThrownBy(() -> cartService.update(new UpdateCartCommand(
                cartId,
                USER_ID,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of("EXPIRED"),
                null,
                null
        )))
                .isInstanceOf(CartException.class)
                .hasMessage("Discount code EXPIRED was not accepted by the merchant.");

        assertThat(cartRepository.saveCount).isZero();
        assertThat(cartRepository.carts.get(cartId).getTotalAmount()).isNull();
        assertThat(cartRepository.carts.get(cartId).getCheckoutUrl()).isEqualTo("https://merchant.example/checkout");
    }

    @Test
    void createIgnoresNullRemoteCartLinesWhenSavingSnapshot() {
        cartClient.cartToolResult = cartToolResult(Arrays.asList(null, cartLine()), null);

        CartResult result = cartService.create(new CreateCartCommand(
                USER_ID,
                merchant.getId(),
                null,
                List.of(new CreateCartCommand.AddItem("gid://shopify/ProductVariant/1", 1)),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null
        ));

        assertThat(result.totalQuantity()).isEqualTo(1);
        assertThat(result.lines()).hasSize(1);
        assertThat(result.lines().getFirst().remoteCartLineId()).isEqualTo("gid://shopify/CartLine/1");
    }

    @Test
    void checkoutRefreshesWhenStoredCheckoutUrlIsMissing() {
        UUID cartId = UUID.randomUUID();
        cartRepository.save(cart(cartId, null));

        CheckoutResult result = cartService.checkout(new GetCheckoutQuery(cartId, USER_ID, false));

        assertThat(result.checkoutUrl()).isEqualTo("https://merchant.example/checkout");
        assertThat(cartClient.getCount).isEqualTo(1);
        assertThat(cartClient.lastRemoteCartId).isEqualTo("gid://shopify/Cart/1");
        assertImportedCandle();
    }

    @Test
    void checkoutReturnsStoredUrlWithoutRefresh() {
        UUID cartId = UUID.randomUUID();
        cartRepository.save(cart(cartId, "https://merchant.example/stored-checkout"));

        CheckoutResult result = cartService.checkout(new GetCheckoutQuery(cartId, USER_ID, false));

        assertThat(result.checkoutUrl()).isEqualTo("https://merchant.example/stored-checkout");
        assertThat(cartClient.getCount).isZero();
        assertImportedCandle();
    }

    @Test
    void checkoutRefreshesWhenRequested() {
        UUID cartId = UUID.randomUUID();
        cartRepository.save(cart(cartId, "https://merchant.example/stored-checkout"));

        CheckoutResult result = cartService.checkout(new GetCheckoutQuery(cartId, USER_ID, true));

        assertThat(result.checkoutUrl()).isEqualTo("https://merchant.example/checkout");
        assertThat(cartClient.getCount).isEqualTo(1);
        assertImportedCandle();
    }

    @Test
    void getWithDifferentUserReturnsNotFoundWithoutRemoteRefresh() {
        UUID cartId = UUID.randomUUID();
        cartRepository.save(cart(cartId, "https://merchant.example/checkout"));

        assertThatThrownBy(() -> cartService.get(new GetCartQuery(cartId, OTHER_USER_ID, true)))
                .isInstanceOf(CartException.class)
                .hasMessage("Cart not found: " + cartId)
                .satisfies(exception -> assertThat(((CartException) exception).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(cartClient.getCount).isZero();
        assertThat(merchantRepository.findByIdCount).isZero();
    }

    @Test
    void updateWithDifferentUserReturnsNotFoundWithoutRemoteUpdate() {
        UUID cartId = UUID.randomUUID();
        cartRepository.save(cart(cartId, "https://merchant.example/checkout"));

        assertThatThrownBy(() -> cartService.update(new UpdateCartCommand(
                cartId,
                OTHER_USER_ID,
                List.of(new UpdateCartCommand.AddItem("gid://shopify/ProductVariant/2", 1)),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null
        )))
                .isInstanceOf(CartException.class)
                .hasMessage("Cart not found: " + cartId)
                .satisfies(exception -> assertThat(((CartException) exception).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(cartClient.updateCount).isZero();
        assertThat(merchantRepository.findByIdCount).isZero();
    }

    @Test
    void checkoutWithDifferentUserReturnsNotFoundWithoutRemoteRefresh() {
        UUID cartId = UUID.randomUUID();
        cartRepository.save(cart(cartId, "https://merchant.example/checkout"));

        assertThatThrownBy(() -> cartService.checkout(new GetCheckoutQuery(cartId, OTHER_USER_ID, true)))
                .isInstanceOf(CartException.class)
                .hasMessage("Cart not found: " + cartId)
                .satisfies(exception -> assertThat(((CartException) exception).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(cartClient.getCount).isZero();
        assertThat(merchantRepository.findByIdCount).isZero();
    }

    @Test
    void replaceLinesUpdatesExistingLineForMatchingRemoteLineId() {
        UUID cartId = UUID.randomUUID();
        UUID cartLineId = UUID.randomUUID();
        Cart cart = cart(cartId, "https://merchant.example/checkout", cartLineId);
        CartLine replacementLine = CartLine.builder()
                .remoteCartLineId("gid://shopify/CartLine/1")
                .productId("gid://shopify/Product/2")
                .productTitle("Updated Candle")
                .productVariantId("gid://shopify/ProductVariant/2")
                .variantTitle("Updated")
                .quantity(3)
                .totalAmount("44.85")
                .subtotalAmount("44.85")
                .currency("USD")
                .rawLineResponse("{\"updated\":true}")
                .createdAt(Instant.parse("2026-06-16T11:06:00Z"))
                .updatedAt(Instant.parse("2026-06-16T11:06:00Z"))
                .build();

        cart.replaceLines(List.of(replacementLine));

        assertThat(cart.getLines()).hasSize(1);
        assertThat(cart.getLines().getFirst().getId()).isEqualTo(cartLineId);
        assertThat(cart.getLines().getFirst().getProductTitle()).isEqualTo("Updated Candle");
        assertThat(cart.getLines().getFirst().getQuantity()).isEqualTo(3);
    }

    private Merchant merchant() {
        Instant now = Instant.parse("2026-06-16T11:05:00Z");
        return Merchant.builder()
                .id(UUID.randomUUID())
                .domain("merchant.example")
                .ucpUrl("https://merchant.example/.well-known/ucp.json")
                .ucpVersion("1.0")
                .advertisedMcpEndpoint("https://merchant.example/api/mcp")
                .profileHash("hash")
                .name("Merchant")
                .description("Description")
                .about("About")
                .targetAudience("Customers")
                .profileQuestion("Question")
                .profileAnswerRaw("Answer")
                .active(true)
                .lastProfiledAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private Cart cart(UUID cartId, String checkoutUrl) {
        return cart(cartId, checkoutUrl, UUID.randomUUID());
    }

    private Cart cart(UUID cartId, String checkoutUrl, UUID cartLineId) {
        Instant now = Instant.parse("2026-06-16T11:05:00Z");
        CartLine line = CartLine.builder()
                .id(cartLineId)
                .remoteCartLineId("gid://shopify/CartLine/1")
                .productId("gid://shopify/Product/1")
                .productTitle("Candle")
                .productVariantId("gid://shopify/ProductVariant/1")
                .variantTitle("3x6")
                .quantity(1)
                .rawLineResponse("{}")
                .createdAt(now)
                .updatedAt(now)
                .build();
        return Cart.builder()
                .id(cartId)
                .userId(USER_ID)
                .merchantId(merchant.getId())
                .merchantDomain(merchant.getDomain())
                .endpoint("https://merchant.example/api/mcp")
                .remoteCartId("gid://shopify/Cart/1")
                .remoteCartIdHash("hash")
                .checkoutUrl(checkoutUrl)
                .rawCartResponse("{}")
                .totalQuantity(1)
                .active(true)
                .remoteCreatedAt(CART_REMOTE_CREATED_AT)
                .remoteUpdatedAt(CART_REMOTE_UPDATED_AT)
                .createdAt(now)
                .updatedAt(now)
                .refreshedAt(now)
                .lines(new ArrayList<>(List.of(line)))
                .build();
    }

    private void assertImportedCandle() {
        assertThat(userInventoryService.lastCommand).isNotNull();
        assertThat(userInventoryService.lastCommand.userId()).isEqualTo(USER_ID);
        assertThat(userInventoryService.lastCommand.items()).singleElement().satisfies(item -> {
            assertThat(item.productKey()).isEqualTo("merchant.example:gid://shopify/ProductVariant/1");
            assertThat(item.name()).isEqualTo("Candle");
            assertThat(item.brand()).isEqualTo("merchant.example");
            assertThat(item.quantity()).isEqualTo(1);
            assertThat(item.purchasedAt()).isEqualTo(CART_REMOTE_UPDATED_AT);
        });
    }

    private CartToolResult cartToolResult() {
        return cartToolResult(List.of(cartLine()), 1, List.of());
    }

    static class FakeUserInventoryService extends UserInventoryService {

        private ImportPurchasedInventoryItemsCommand lastCommand;

        FakeUserInventoryService() {
            super(null, null, null, null);
        }

        @Override
        public void importPurchasedItems(ImportPurchasedInventoryItemsCommand command) {
            lastCommand = command;
        }
    }

    private CartToolResult cartToolResult(List<CartToolResponse.Line> lines, Integer totalQuantity) {
        return cartToolResult(lines, totalQuantity, List.of());
    }

    private CartToolResult cartToolResult(
            List<CartToolResponse.Line> lines,
            Integer totalQuantity,
            List<CartToolResponse.DeliveryGroup> deliveryGroups
    ) {
        CartToolResponse response = new CartToolResponse(
                "Checkout when ready",
                new CartToolResponse.Cart(
                        "gid://shopify/Cart/1",
                        CART_REMOTE_CREATED_AT,
                        CART_REMOTE_UPDATED_AT,
                        lines,
                        new CartToolResponse.Cost(
                                new CartToolResponse.Money("14.95", "USD"),
                                new CartToolResponse.Money("14.95", "USD")
                        ),
                        totalQuantity,
                        "https://merchant.example/checkout",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        deliveryGroups
                ),
                List.of()
        );
        return new CartToolResult(
                "https://merchant.example/api/mcp",
                raw(response),
                response
        );
    }

    private CartToolResult cartToolResultWithAppliedCodes() {
        CartToolResponse response = new CartToolResponse(
                "Checkout when ready",
                new CartToolResponse.Cart(
                        "gid://shopify/Cart/1",
                        CART_REMOTE_CREATED_AT,
                        CART_REMOTE_UPDATED_AT,
                        List.of(cartLine()),
                        new CartToolResponse.Cost(
                                new CartToolResponse.Money("7.95", "USD"),
                                new CartToolResponse.Money("14.95", "USD")
                        ),
                        1,
                        "https://merchant.example/checkout",
                        List.of(new CartToolResponse.AppliedCode(
                                "SAVE5",
                                "Spring discount",
                                true,
                                new CartToolResponse.Money("5.00", "USD")
                        )),
                        List.of(),
                        List.of(),
                        List.of(new CartToolResponse.AppliedCode(
                                "1234",
                                "Gift card",
                                true,
                                new CartToolResponse.Money("2.00", "USD")
                        )),
                        List.of(),
                        List.of()
                ),
                List.of()
        );
        return new CartToolResult(
                "https://merchant.example/api/mcp",
                raw(response),
                response
        );
    }

    private CartToolResponse.Line cartLine() {
        return new CartToolResponse.Line(
                "gid://shopify/CartLine/1",
                1,
                new CartToolResponse.Cost(
                        new CartToolResponse.Money("14.95", "USD"),
                        new CartToolResponse.Money("14.95", "USD")
                ),
                new CartToolResponse.Merchandise(
                        "gid://shopify/ProductVariant/1",
                        "3x6",
                        new CartToolResponse.Product("gid://shopify/Product/1", "Candle")
                )
        );
    }

    private CartToolResponse.DeliveryGroup deliveryGroup() {
        CartToolResponse.DeliveryOption standard = new CartToolResponse.DeliveryOption(
                "standard",
                "Standard",
                "Arrives in 3 to 5 business days",
                null,
                new CartToolResponse.Money("5.00", "USD"),
                null,
                "shipping",
                "3 to 5 business days",
                null,
                null,
                true
        );
        CartToolResponse.DeliveryOption express = new CartToolResponse.DeliveryOption(
                "express",
                "Express",
                "Arrives in 1 to 2 business days",
                null,
                new CartToolResponse.Money("12.00", "USD"),
                null,
                "shipping",
                "1 to 2 business days",
                null,
                null,
                false
        );
        return new CartToolResponse.DeliveryGroup(
                "delivery-group-1",
                "delivery-group-handle-1",
                List.of(standard, express),
                standard
        );
    }

    private String raw(CartToolResponse response) {
        try {
            return new ObjectMapper().writeValueAsString(response);
        } catch (JacksonException exception) {
            throw new AssertionError(exception);
        }
    }

    static class FakeCartClient extends CartClient {

        private CartToolResult cartToolResult;
        private UpdateCartArguments lastUpdateArguments;
        private RuntimeException updateException;
        private String lastRemoteCartId;
        private int updateCount;
        private int getCount;

        FakeCartClient() {
            super(null, null);
        }

        @Override
        public CartToolResult updateCart(MerchantCartProvider provider, UpdateCartArguments arguments) {
            updateCount++;
            lastUpdateArguments = arguments;
            if (updateException != null) {
                throw updateException;
            }
            return cartToolResult;
        }

        @Override
        public CartToolResult getCart(MerchantCartProvider provider, String remoteCartId) {
            getCount++;
            lastRemoteCartId = remoteCartId;
            return cartToolResult;
        }
    }

    static class FakeMerchantRepository {

        private final Map<UUID, Merchant> merchantsById = new HashMap<>();
        private final Map<String, Merchant> merchantsByDomain = new HashMap<>();
        private int findByIdCount;

        void save(Merchant merchant) {
            merchantsById.put(merchant.getId(), merchant);
            merchantsByDomain.put(merchant.getDomain(), merchant);
        }

        MerchantRepository proxy() {
            return (MerchantRepository) Proxy.newProxyInstance(
                    MerchantRepository.class.getClassLoader(),
                    new Class<?>[]{MerchantRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findById" -> {
                            findByIdCount++;
                            yield Optional.ofNullable(merchantsById.get(args[0]));
                        }
                        case "findByDomain" -> Optional.ofNullable(merchantsByDomain.get(args[0]));
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }

    static class FakeCartRepository {

        private final Map<UUID, Cart> carts = new HashMap<>();
        private int saveCount;

        void save(Cart cart) {
            carts.put(cart.getId(), cart);
        }

        CartRepository proxy() {
            return (CartRepository) Proxy.newProxyInstance(
                    CartRepository.class.getClassLoader(),
                    new Class<?>[]{CartRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findWithLinesByIdAndUserId" -> {
                            Cart cart = carts.get(args[0]);
                            yield cart == null || !cart.getUserId().equals(args[1])
                                    ? Optional.empty()
                                    : Optional.of(cart);
                        }
                        case "save" -> {
                            Cart cart = (Cart) args[0];
                            save(cart);
                            saveCount++;
                            yield cart;
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }
}
