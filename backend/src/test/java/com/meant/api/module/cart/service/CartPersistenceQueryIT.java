package com.meant.api.module.cart.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.PostgresIntegrationTestSupport;
import com.meant.api.module.cart.constant.CartAppliedCodeType;
import com.meant.api.module.cart.entity.Cart;
import com.meant.api.module.cart.entity.CartAppliedCode;
import com.meant.api.module.cart.entity.CartLine;
import com.meant.api.module.cart.service.command.TransferCartOwnershipCommand;
import com.meant.api.module.user.entity.User;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class CartPersistenceQueryIT extends PostgresIntegrationTestSupport {

    @Autowired
    private CartPersistenceService cartPersistenceService;

    @Autowired
    private CartOwnershipTransferService cartOwnershipTransferService;

    @Autowired
    private EntityManager entityManager;

    @Test
    void activeCartPageLoadsBothCollectionsInThreeSelects() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-18T10:00:00Z");
        entityManager.persist(User.builder()
                .id(userId)
                .email("cart-batch-" + userId + "@example.test")
                .newsletter(false)
                .createdAt(now)
                .updatedAt(now)
                .build());
        for (int index = 0; index < 20; index++) {
            entityManager.persist(cart(userId, index, now.plusSeconds(index)));
        }
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();
        statistics.clear();

        List<Cart> carts = cartPersistenceService.findActiveCarts(userId, 0, 20);

        assertThat(carts).hasSize(20);
        assertThat(carts).allSatisfy(cart -> {
            assertThat(cart.getLines()).hasSize(1);
            assertThat(cart.getAppliedCodes()).hasSize(1);
        });
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
    }

    @Test
    void ownershipTransferPersistsTheGuestCartUnderThePermanentUser() {
        UUID guestUserId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-05T20:00:00Z");
        entityManager.persist(user(guestUserId, "guest", now));
        entityManager.persist(user(targetUserId, "target", now));
        Cart guestCart = cart(guestUserId, 100, now);
        entityManager.persist(guestCart);
        entityManager.flush();
        entityManager.clear();

        assertThat(cartOwnershipTransferService.transfer(
                new TransferCartOwnershipCommand(guestUserId, targetUserId)))
                .containsExactly(guestCart.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(cartPersistenceService.findActiveCarts(guestUserId, 0, 20)).isEmpty();
        assertThat(cartPersistenceService.findActiveCarts(targetUserId, 0, 20))
                .extracting(Cart::getId)
                .containsExactly(guestCart.getId());
    }

    private User user(UUID userId, String prefix, Instant now) {
        return User.builder()
                .id(userId)
                .email(prefix + "-cart-transfer-" + userId + "@example.test")
                .newsletter(false)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private Cart cart(UUID userId, int index, Instant now) {
        Cart cart = Cart.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .provider("GENERIC_UCP")
                .externalMerchantId("merchant-1")
                .merchantDomain("merchant.example")
                .routingDomain("merchant.example")
                .routingScopeKey("GENERIC_UCP:merchant:merchant-1")
                .endpoint("https://merchant.example/mcp")
                .remoteCartId("remote-cart-" + index)
                .remoteCartIdHash("remote-cart-hash-" + index)
                .rawCartResponse("{}")
                .totalQuantity(1)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .refreshedAt(now)
                .build();
        cart.replaceLines(List.of(CartLine.builder()
                .remoteCartLineId("remote-line-" + index)
                .productVariantId("variant-" + index)
                .quantity(1)
                .rawLineResponse("{}")
                .createdAt(now)
                .updatedAt(now)
                .build()));
        cart.replaceAppliedCodes(List.of(CartAppliedCode.builder()
                .type(CartAppliedCodeType.DISCOUNT)
                .code("CODE-" + index)
                .displayOrder(0)
                .build()));
        return cart;
    }
}
