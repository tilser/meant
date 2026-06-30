package com.meant.api.plugin.checkout.common.service;

import com.meant.api.plugin.checkout.common.entity.CheckoutCompletionState;
import com.meant.api.plugin.checkout.common.entity.CheckoutCompletionStatus;
import com.meant.api.plugin.checkout.common.exception.UcpCheckoutSafetyException;
import com.meant.api.plugin.checkout.common.repository.CheckoutCompletionStateRepository;
import com.meant.api.plugin.checkout.common.service.command.AuthorizeCheckoutCompletionCommand;
import com.meant.api.plugin.checkout.common.service.command.StartCheckoutCompletionCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class CheckoutCompletionStateStore {

    private final CheckoutCompletionStateRepository repository;

    @Transactional
    public CheckoutCompletionState authorize(@NotNull @Valid AuthorizeCheckoutCompletionCommand command) {
        String checkoutIdHash = hash(command.checkoutId());
        Instant now = Instant.now();
        return repository.findByCheckoutIdHash(checkoutIdHash)
                .map(existing -> authorizeExisting(existing, command, now))
                .orElseGet(() -> repository.save(CheckoutCompletionState.builder()
                        .cartId(command.cartId())
                        .checkoutId(command.checkoutId().trim())
                        .checkoutIdHash(checkoutIdHash)
                        .status(CheckoutCompletionStatus.AUTHORIZED_TO_COMPLETE)
                        .createdAt(now)
                        .updatedAt(now)
                        .build()));
    }

    @Transactional
    public boolean tryStartCompletion(@NotNull @Valid StartCheckoutCompletionCommand command) {
        return repository.compareAndSetStatus(
                hash(command.checkoutId()),
                CheckoutCompletionStatus.AUTHORIZED_TO_COMPLETE,
                CheckoutCompletionStatus.COMPLETION_IN_FLIGHT,
                Instant.now()
        ) == 1;
    }

    @Transactional
    public boolean tryCancel(@NotNull @Valid StartCheckoutCompletionCommand command) {
        return repository.compareAndSetStatus(
                hash(command.checkoutId()),
                CheckoutCompletionStatus.AUTHORIZED_TO_COMPLETE,
                CheckoutCompletionStatus.CANCELED,
                Instant.now()
        ) == 1;
    }

    @Transactional
    public CheckoutCompletionState markCompleted(@NotNull @Valid StartCheckoutCompletionCommand command) {
        CheckoutCompletionState state = findLocked(command);
        if (state.getStatus() != CheckoutCompletionStatus.COMPLETION_IN_FLIGHT) {
            throw new UcpCheckoutSafetyException("Checkout completion is not in flight: " + command.checkoutId());
        }
        state.markCompleted(Instant.now());
        return repository.save(state);
    }

    @Transactional
    public CheckoutCompletionState markCanceled(@NotNull @Valid StartCheckoutCompletionCommand command) {
        CheckoutCompletionState state = findLocked(command);
        if (state.getStatus() != CheckoutCompletionStatus.CANCELED) {
            throw new UcpCheckoutSafetyException("Checkout cancellation was not reserved: " + command.checkoutId());
        }
        state.markCanceled(Instant.now());
        return repository.save(state);
    }

    @Transactional
    public CheckoutCompletionState releaseCompletionStart(@NotNull @Valid StartCheckoutCompletionCommand command) {
        CheckoutCompletionState state = findLocked(command);
        if (state.getStatus() != CheckoutCompletionStatus.COMPLETION_IN_FLIGHT) {
            throw new UcpCheckoutSafetyException("Checkout completion is not in flight: " + command.checkoutId());
        }
        state.transitionTo(CheckoutCompletionStatus.AUTHORIZED_TO_COMPLETE, Instant.now());
        return repository.save(state);
    }

    @Transactional(readOnly = true)
    public CheckoutCompletionState find(@NotNull @Valid StartCheckoutCompletionCommand command) {
        return repository.findReadOnlyByCheckoutIdHash(hash(command.checkoutId()))
                .orElseThrow(() -> new UcpCheckoutSafetyException(
                        "Checkout completion state was not authorized: " + command.checkoutId()
                ));
    }

    private CheckoutCompletionState findLocked(StartCheckoutCompletionCommand command) {
        return repository.findByCheckoutIdHash(hash(command.checkoutId()))
                .orElseThrow(() -> new UcpCheckoutSafetyException(
                        "Checkout completion state was not authorized: " + command.checkoutId()
                ));
    }

    private CheckoutCompletionState authorizeExisting(
            CheckoutCompletionState existing,
            AuthorizeCheckoutCompletionCommand command,
            Instant now
    ) {
        if (existing.getStatus() == CheckoutCompletionStatus.COMPLETION_IN_FLIGHT
                || existing.getStatus() == CheckoutCompletionStatus.COMPLETED) {
            throw new UcpCheckoutSafetyException("Checkout completion cannot be re-authorized after completion starts");
        }
        existing.authorize(command.cartId(), now);
        return repository.save(existing);
    }

    private String hash(String value) {
        try {
            String normalized = value == null ? "" : value.trim();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new UcpCheckoutSafetyException("SHA-256 hash algorithm is unavailable", exception);
        }
    }
}
