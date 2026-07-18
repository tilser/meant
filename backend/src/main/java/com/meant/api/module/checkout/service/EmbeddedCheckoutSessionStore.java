package com.meant.api.module.checkout.service;

import com.meant.api.module.checkout.constant.EmbeddedCheckoutSessionStatus;
import com.meant.api.module.checkout.entity.EmbeddedCheckoutSession;
import com.meant.api.module.checkout.exception.EmbeddedCheckoutException;
import com.meant.api.module.checkout.properties.EmbeddedCheckoutProperties;
import com.meant.api.module.checkout.repository.EmbeddedCheckoutSessionRepository;
import com.meant.api.module.checkout.service.command.CreateEmbeddedCheckoutSessionCommand;
import com.meant.api.module.checkout.service.command.UseEmbeddedCheckoutSessionCommand;
import com.meant.api.module.checkout.service.dto.EmbeddedCheckoutSessionBinding;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class EmbeddedCheckoutSessionStore {
    private final EmbeddedCheckoutSessionRepository repository;
    private final EmbeddedCheckoutProperties properties;
    private final Clock clock;

    @Transactional
    public EmbeddedCheckoutSessionBinding create(@NotNull @Valid CreateEmbeddedCheckoutSessionCommand command) {
        Instant now = clock.instant();
        EmbeddedCheckoutSession session = repository.save(EmbeddedCheckoutSession.builder()
                .userId(command.userId())
                .cartId(command.cartId())
                .checkoutId(command.checkoutId().trim())
                .checkoutAttemptId(command.checkoutAttemptId())
                .merchantIntegrationId(command.merchantIntegrationId())
                .routingScopeKey(command.routingScopeKey().trim())
                .allowedOrigin(command.allowedOrigin().trim())
                .protocolVersion(command.protocolVersion().trim())
                .status(EmbeddedCheckoutSessionStatus.ACTIVE)
                .expiresAt(now.plus(properties.sessionTtl()))
                .createdAt(now)
                .build());
        return binding(session);
    }

    @Transactional(readOnly = true)
    public EmbeddedCheckoutSessionBinding requireActive(@NotNull @Valid UseEmbeddedCheckoutSessionCommand command) {
        EmbeddedCheckoutSession session = validatedBinding(find(command.sessionId()), command);
        try {
            session.requireCompletable(clock.instant());
        } catch (IllegalStateException exception) {
            throw EmbeddedCheckoutException.conflict(exception.getMessage());
        }
        return binding(session);
    }

    @Transactional
    public EmbeddedCheckoutSessionBinding complete(@NotNull @Valid UseEmbeddedCheckoutSessionCommand command) {
        EmbeddedCheckoutSession session = validatedBinding(findForUpdate(command.sessionId()), command);
        try {
            session.complete(clock.instant());
        } catch (IllegalStateException exception) {
            throw EmbeddedCheckoutException.conflict(exception.getMessage());
        }
        return binding(repository.save(session));
    }

    @Transactional
    public EmbeddedCheckoutSessionBinding cancel(@NotNull @Valid UseEmbeddedCheckoutSessionCommand command) {
        EmbeddedCheckoutSession session = validatedBinding(findForUpdate(command.sessionId()), command);
        try {
            session.cancel(clock.instant());
        } catch (IllegalStateException exception) {
            throw EmbeddedCheckoutException.conflict(exception.getMessage());
        }
        return binding(repository.save(session));
    }

    @Transactional
    public EmbeddedCheckoutSessionBinding acknowledgeOpened(
            @NotNull @Valid UseEmbeddedCheckoutSessionCommand command) {
        EmbeddedCheckoutSession session = validatedBinding(findForUpdate(command.sessionId()), command);
        try {
            session.acknowledgeOpened(clock.instant());
        } catch (IllegalStateException exception) {
            throw EmbeddedCheckoutException.conflict(exception.getMessage());
        }
        return binding(repository.save(session));
    }

    private EmbeddedCheckoutSession validatedBinding(
            EmbeddedCheckoutSession session, UseEmbeddedCheckoutSessionCommand command) {
        if (!session.getUserId().equals(command.userId())
                || !session.getCartId().equals(command.cartId())
                || !session.getCheckoutId().equals(command.checkoutId().trim())
                || !session.getCheckoutAttemptId().equals(command.checkoutAttemptId())
                || !java.util.Objects.equals(session.getMerchantIntegrationId(), command.merchantIntegrationId())
                || !session.getRoutingScopeKey().equals(command.routingScopeKey().trim())
                || !session.getAllowedOrigin().equals(command.allowedOrigin().trim())) {
            throw EmbeddedCheckoutException.forbidden("Embedded checkout session binding does not match");
        }
        return session;
    }

    private EmbeddedCheckoutSession find(java.util.UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> EmbeddedCheckoutException.forbidden("Embedded checkout session not found"));
    }

    private EmbeddedCheckoutSession findForUpdate(java.util.UUID id) {
        return repository.findForUpdate(id)
                .orElseThrow(() -> EmbeddedCheckoutException.forbidden("Embedded checkout session not found"));
    }

    private EmbeddedCheckoutSessionBinding binding(EmbeddedCheckoutSession session) {
        return new EmbeddedCheckoutSessionBinding(session.getId(), session.getCartId(), session.getCheckoutId(),
                session.getCheckoutAttemptId(), session.getAllowedOrigin(), session.getProtocolVersion(),
                session.getExpiresAt());
    }
}
