package com.meant.api.plugin.checkout.common.service;

import com.meant.api.plugin.checkout.common.entity.CheckoutIdempotencyKey;
import com.meant.api.plugin.checkout.common.entity.CheckoutIdempotencyStatus;
import com.meant.api.plugin.checkout.common.exception.UcpCheckoutSafetyException;
import com.meant.api.plugin.checkout.common.repository.CheckoutIdempotencyKeyRepository;
import com.meant.api.plugin.checkout.common.service.command.RecordIdempotencyResponseCommand;
import com.meant.api.plugin.checkout.common.service.command.ReserveIdempotencyKeyCommand;
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
public class IdempotencyKeyStore {

    private final CheckoutIdempotencyKeyRepository repository;

    @Transactional
    public CheckoutIdempotencyKey reserve(@NotNull @Valid ReserveIdempotencyKeyCommand command) {
        String bodyHash = hash(command.body());
        return repository.findByIdempotencyKey(command.idempotencyKey())
                .map(existing -> existingOrConflict(existing, bodyHash))
                .orElseGet(() -> create(command, bodyHash));
    }

    @Transactional
    public CheckoutIdempotencyKey recordResponse(@NotNull @Valid RecordIdempotencyResponseCommand command) {
        CheckoutIdempotencyKey record = repository.findByIdempotencyKey(command.idempotencyKey())
                .orElseThrow(() -> new UcpCheckoutSafetyException(
                        "Idempotency key was not reserved: " + command.idempotencyKey()
                ));
        Instant now = Instant.now();
        if (command.status() == CheckoutIdempotencyStatus.COMPLETION_IN_FLIGHT) {
            record.markInFlight(command.remoteResponse(), now);
        } else if (command.status() == CheckoutIdempotencyStatus.COMPLETED) {
            record.markCompleted(command.remoteResponse(), command.finalOrderRef(), now);
        } else if (command.status() == CheckoutIdempotencyStatus.FAILED) {
            record.markFailed(command.remoteResponse(), now);
        } else {
            throw new UcpCheckoutSafetyException("Unsupported idempotency response status: " + command.status());
        }
        return repository.save(record);
    }

    public String bodyHash(byte[] body) {
        return hash(body);
    }

    private CheckoutIdempotencyKey existingOrConflict(CheckoutIdempotencyKey existing, String bodyHash) {
        if (!existing.getBodyHash().equals(bodyHash)) {
            throw new UcpCheckoutSafetyException("Idempotency key was reused with a different request body");
        }
        return existing;
    }

    private CheckoutIdempotencyKey create(ReserveIdempotencyKeyCommand command, String bodyHash) {
        Instant now = Instant.now();
        return repository.save(CheckoutIdempotencyKey.builder()
                .idempotencyKey(command.idempotencyKey().trim())
                .bodyHash(bodyHash)
                .checkoutId(command.checkoutId().trim())
                .consentId(command.consentId())
                .amount(command.amount())
                .currency(command.currency().trim().toUpperCase(java.util.Locale.ROOT))
                .merchantId(command.merchantId())
                .status(CheckoutIdempotencyStatus.RESERVED)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private String hash(byte[] body) {
        try {
            byte[] safeBody = body == null ? new byte[0] : body;
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(safeBody));
        } catch (NoSuchAlgorithmException exception) {
            throw new UcpCheckoutSafetyException("SHA-256 hash algorithm is unavailable", exception);
        }
    }

    public String stringHash(String value) {
        return hash(value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8));
    }
}
