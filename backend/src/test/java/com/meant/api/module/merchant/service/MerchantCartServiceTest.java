package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.entity.MerchantCart;
import com.meant.api.module.merchant.entity.MerchantCartLine;
import com.meant.api.module.merchant.repository.MerchantCartRepository;
import com.meant.api.module.merchant.repository.MerchantRepository;
import com.meant.api.module.merchant.service.command.CreateMerchantCartCommand;
import com.meant.api.module.merchant.service.command.UpdateMerchantCartCommand;
import com.meant.api.module.merchant.service.dto.CartToolResponse;
import com.meant.api.module.merchant.service.dto.CartToolResult;
import com.meant.api.module.merchant.service.dto.MerchantCartResult;
import com.meant.api.module.merchant.service.dto.MerchantCheckoutResult;
import com.meant.api.module.merchant.service.dto.UpdateCartArguments;
import com.meant.api.module.merchant.service.query.GetMerchantCartQuery;
import com.meant.api.module.merchant.service.query.GetMerchantCheckoutQuery;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.ObjectMapper;

class MerchantCartServiceTest {

    private FakeMerchantRepository merchantRepository;
    private FakeCartRepository merchantCartRepository;
    private FakeCartClient merchantCartClient;
    private MerchantCartService merchantCartService;
    private Merchant merchant;

    @BeforeEach
    void setUp() {
        merchantRepository = new FakeMerchantRepository();
        merchantCartRepository = new FakeCartRepository();
        merchantCartClient = new FakeCartClient();
        merchantCartService = new MerchantCartService(
                merchantRepository.proxy(),
                merchantCartRepository.proxy(),
                merchantCartClient,
                transactionManager(),
                new ObjectMapper()
        );
        merchant = merchant();
        merchantRepository.save(merchant);
        merchantCartClient.cartToolResult = cartToolResult();
    }

    @Test
    void createCallsRemoteCartAndSavesSnapshot() {
        MerchantCartResult result = merchantCartService.create(new CreateMerchantCartCommand(
                merchant.getId(),
                null,
                List.of(new CreateMerchantCartCommand.AddItem("gid://shopify/ProductVariant/1", 1)),
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
        assertThat(merchantCartRepository.saveCount).isEqualTo(1);
        assertThat(merchantCartClient.updateCount).isEqualTo(1);
    }

    @Test
    void getWithoutRefreshUsesStoredSnapshotOnly() {
        UUID cartId = UUID.randomUUID();
        merchantCartRepository.save(cart(cartId, "https://merchant.example/checkout"));

        MerchantCartResult result = merchantCartService.get(new GetMerchantCartQuery(cartId, false));

        assertThat(result.checkoutUrl()).isEqualTo("https://merchant.example/checkout");
        assertThat(merchantCartClient.getCount).isZero();
    }

    @Test
    void updateMapsLocalCartLineIdsToRemoteCartLineIds() {
        UUID cartId = UUID.randomUUID();
        UUID cartLineId = UUID.randomUUID();
        merchantCartRepository.save(cart(cartId, "https://merchant.example/checkout", cartLineId));

        merchantCartService.update(new UpdateMerchantCartCommand(
                cartId,
                List.of(),
                List.of(new UpdateMerchantCartCommand.UpdateItem(cartLineId, null, 2)),
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

        assertThat(merchantCartClient.lastUpdateArguments.updateItems()).extracting("id")
                .containsExactly("gid://shopify/CartLine/1");
        assertThat(merchantCartClient.lastUpdateArguments.removeLineIds()).containsExactly("gid://shopify/CartLine/1");
    }

    @Test
    void createIgnoresNullRemoteCartLinesWhenSavingSnapshot() {
        merchantCartClient.cartToolResult = cartToolResult(Arrays.asList(null, cartLine()), null);

        MerchantCartResult result = merchantCartService.create(new CreateMerchantCartCommand(
                merchant.getId(),
                null,
                List.of(new CreateMerchantCartCommand.AddItem("gid://shopify/ProductVariant/1", 1)),
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
        merchantCartRepository.save(cart(cartId, null));

        MerchantCheckoutResult result = merchantCartService.checkout(new GetMerchantCheckoutQuery(cartId, false));

        assertThat(result.checkoutUrl()).isEqualTo("https://merchant.example/checkout");
        assertThat(merchantCartClient.getCount).isEqualTo(1);
        assertThat(merchantCartClient.lastRemoteCartId).isEqualTo("gid://shopify/Cart/1");
    }

    @Test
    void replaceLinesUpdatesExistingLineForMatchingRemoteLineId() {
        UUID cartId = UUID.randomUUID();
        UUID cartLineId = UUID.randomUUID();
        MerchantCart cart = cart(cartId, "https://merchant.example/checkout", cartLineId);
        MerchantCartLine replacementLine = MerchantCartLine.builder()
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

    private PlatformTransactionManager transactionManager() {
        return new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        };
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

    private MerchantCart cart(UUID cartId, String checkoutUrl) {
        return cart(cartId, checkoutUrl, UUID.randomUUID());
    }

    private MerchantCart cart(UUID cartId, String checkoutUrl, UUID cartLineId) {
        Instant now = Instant.parse("2026-06-16T11:05:00Z");
        MerchantCartLine line = MerchantCartLine.builder()
                .id(cartLineId)
                .remoteCartLineId("gid://shopify/CartLine/1")
                .productVariantId("gid://shopify/ProductVariant/1")
                .quantity(1)
                .rawLineResponse("{}")
                .createdAt(now)
                .updatedAt(now)
                .build();
        return MerchantCart.builder()
                .id(cartId)
                .merchant(merchant)
                .endpoint("https://merchant.example/api/mcp")
                .remoteCartId("gid://shopify/Cart/1")
                .remoteCartIdHash("hash")
                .checkoutUrl(checkoutUrl)
                .rawCartResponse("{}")
                .totalQuantity(1)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .refreshedAt(now)
                .lines(new ArrayList<>(List.of(line)))
                .build();
    }

    private CartToolResult cartToolResult() {
        return cartToolResult(List.of(cartLine()), 1);
    }

    private CartToolResult cartToolResult(List<CartToolResponse.Line> lines, Integer totalQuantity) {
        return new CartToolResult(
                "https://merchant.example/api/mcp",
                "{}",
                new CartToolResponse(
                        "Checkout when ready",
                        new CartToolResponse.Cart(
                                "gid://shopify/Cart/1",
                                Instant.parse("2026-06-16T11:05:00Z"),
                                Instant.parse("2026-06-16T11:05:01Z"),
                                lines,
                                new CartToolResponse.Cost(
                                        new CartToolResponse.Money("14.95", "USD"),
                                        new CartToolResponse.Money("14.95", "USD")
                                ),
                                totalQuantity,
                                "https://merchant.example/checkout"
                        ),
                        List.of()
                )
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

    static class FakeCartClient extends MerchantCartClient {

        private CartToolResult cartToolResult;
        private UpdateCartArguments lastUpdateArguments;
        private String lastRemoteCartId;
        private int updateCount;
        private int getCount;

        FakeCartClient() {
            super(null, null);
        }

        @Override
        public CartToolResult updateCart(Merchant merchant, UpdateCartArguments arguments) {
            updateCount++;
            lastUpdateArguments = arguments;
            return cartToolResult;
        }

        @Override
        public CartToolResult getCart(Merchant merchant, String remoteCartId) {
            getCount++;
            lastRemoteCartId = remoteCartId;
            return cartToolResult;
        }
    }

    static class FakeMerchantRepository {

        private final Map<UUID, Merchant> merchantsById = new HashMap<>();
        private final Map<String, Merchant> merchantsByDomain = new HashMap<>();

        void save(Merchant merchant) {
            merchantsById.put(merchant.getId(), merchant);
            merchantsByDomain.put(merchant.getDomain(), merchant);
        }

        MerchantRepository proxy() {
            return (MerchantRepository) Proxy.newProxyInstance(
                    MerchantRepository.class.getClassLoader(),
                    new Class<?>[]{MerchantRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findById" -> Optional.ofNullable(merchantsById.get(args[0]));
                        case "findByDomain" -> Optional.ofNullable(merchantsByDomain.get(args[0]));
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }

    static class FakeCartRepository {

        private final Map<UUID, MerchantCart> carts = new HashMap<>();
        private int saveCount;

        void save(MerchantCart cart) {
            carts.put(cart.getId(), cart);
        }

        MerchantCartRepository proxy() {
            return (MerchantCartRepository) Proxy.newProxyInstance(
                    MerchantCartRepository.class.getClassLoader(),
                    new Class<?>[]{MerchantCartRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findWithMerchantAndLinesById" -> Optional.ofNullable(carts.get(args[0]));
                        case "save" -> {
                            MerchantCart cart = (MerchantCart) args[0];
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
