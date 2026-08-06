package com.meant.api.module.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meant.api.module.cart.entity.Cart;
import com.meant.api.module.cart.repository.CartRepository;
import com.meant.api.module.cart.service.command.TransferCartOwnershipCommand;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CartOwnershipTransferServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-05T20:00:00Z");

    private final CartRepository cartRepository = mock(CartRepository.class);
    private final CartOwnershipTransferService service = new CartOwnershipTransferService(
            cartRepository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void transfersEveryLiveGuestCartToThePermanentAccount() {
        UUID guestUserId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        Cart first = cart(guestUserId, NOW.minusSeconds(20));
        Cart second = cart(guestUserId, NOW.minusSeconds(10));
        when(cartRepository.findActiveForOwnershipTransfer(guestUserId, NOW))
                .thenReturn(List.of(first, second));

        List<UUID> transferred = service.transfer(new TransferCartOwnershipCommand(guestUserId, targetUserId));

        assertThat(transferred).containsExactly(first.getId(), second.getId());
        assertThat(first.getUserId()).isEqualTo(targetUserId);
        assertThat(second.getUserId()).isEqualTo(targetUserId);
        assertThat(first.getUpdatedAt()).isEqualTo(NOW);
        assertThat(second.getUpdatedAt()).isEqualTo(NOW);
        verify(cartRepository).findActiveForOwnershipTransfer(guestUserId, NOW);
    }

    @Test
    void doesNothingWhenTheAnonymousAccountWasUpgradedInPlace() {
        UUID userId = UUID.randomUUID();

        assertThat(service.transfer(new TransferCartOwnershipCommand(userId, userId))).isEmpty();

        verifyNoInteractions(cartRepository);
    }

    private Cart cart(UUID userId, Instant updatedAt) {
        return Cart.builder()
                .userId(userId)
                .endpoint("https://merchant.example/api/ucp/mcp")
                .remoteCartId("gid://shopify/Cart/" + UUID.randomUUID())
                .remoteCartIdHash(UUID.randomUUID().toString())
                .rawCartResponse("{}")
                .totalQuantity(1)
                .active(true)
                .createdAt(updatedAt)
                .updatedAt(updatedAt)
                .refreshedAt(updatedAt)
                .build();
    }
}
