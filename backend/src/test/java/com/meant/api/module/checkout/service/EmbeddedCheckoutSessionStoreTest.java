package com.meant.api.module.checkout.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.meant.api.module.checkout.entity.EmbeddedCheckoutSession;
import com.meant.api.module.checkout.exception.EmbeddedCheckoutException;
import com.meant.api.module.checkout.properties.EmbeddedCheckoutProperties;
import com.meant.api.module.checkout.repository.EmbeddedCheckoutSessionRepository;
import com.meant.api.module.checkout.service.command.CreateEmbeddedCheckoutSessionCommand;
import com.meant.api.module.checkout.service.command.UseEmbeddedCheckoutSessionCommand;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmbeddedCheckoutSessionStoreTest {
    private static final Instant NOW = Instant.parse("2026-07-11T20:00:00Z");
    @Mock private EmbeddedCheckoutSessionRepository repository;
    private EmbeddedCheckoutSessionStore store;

    @BeforeEach
    void setUp() {
        store = new EmbeddedCheckoutSessionStore(repository,
                new EmbeddedCheckoutProperties(Duration.ofMinutes(5), "2026-01-23"),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void bindsSessionAndRejectsOwnershipOriginExpiryAndReplay() {
        AtomicReference<EmbeddedCheckoutSession> saved = new AtomicReference<>();
        when(repository.save(any())).thenAnswer(invocation -> {
            EmbeddedCheckoutSession session = invocation.getArgument(0);
            saved.set(session);
            return session;
        });
        UUID userId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        var binding = store.create(new CreateEmbeddedCheckoutSessionCommand(
                userId, cartId, "checkout-1", null, "SHOPIFY:merchant:1", "https://meant.com", "2026-01-23"));
        EmbeddedCheckoutSession session = saved.get();
        when(repository.findForUpdate(binding.sessionId())).thenReturn(Optional.of(session));
        when(repository.findById(binding.sessionId())).thenReturn(Optional.of(session));

        UseEmbeddedCheckoutSessionCommand command = new UseEmbeddedCheckoutSessionCommand(
                binding.sessionId(), userId, cartId, "checkout-1", null, "SHOPIFY:merchant:1", "https://meant.com");
        assertThat(store.requireActive(command).checkoutId()).isEqualTo("checkout-1");
        assertThatThrownBy(() -> store.requireActive(new UseEmbeddedCheckoutSessionCommand(
                binding.sessionId(), userId, cartId, "checkout-1", null,
                "SHOPIFY:merchant:1", "https://attacker.example")))
                .isInstanceOf(EmbeddedCheckoutException.class);
        store.complete(command);
        assertThatThrownBy(() -> store.complete(command)).isInstanceOf(EmbeddedCheckoutException.class);
        assertThatThrownBy(() -> store.requireActive(new UseEmbeddedCheckoutSessionCommand(
                binding.sessionId(), UUID.randomUUID(), cartId, "checkout-1", null,
                "SHOPIFY:merchant:1", "https://meant.com")))
                .isInstanceOf(EmbeddedCheckoutException.class);
    }

    @Test
    void rejectsExpiredSession() {
        UUID id = UUID.randomUUID();
        EmbeddedCheckoutSession expired = EmbeddedCheckoutSession.builder()
                .id(id).userId(UUID.randomUUID()).cartId(UUID.randomUUID()).checkoutId("checkout-1")
                .routingScopeKey("scope").allowedOrigin("https://meant.com").protocolVersion("2026-01-23")
                .status(com.meant.api.module.checkout.constant.EmbeddedCheckoutSessionStatus.ACTIVE)
                .expiresAt(NOW).createdAt(NOW.minusSeconds(60)).build();
        when(repository.findById(id)).thenReturn(Optional.of(expired));
        assertThatThrownBy(() -> store.requireActive(new UseEmbeddedCheckoutSessionCommand(
                id, expired.getUserId(), expired.getCartId(), "checkout-1", null, "scope", "https://meant.com")))
                .isInstanceOf(EmbeddedCheckoutException.class);
    }
}
