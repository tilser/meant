package com.meant.api.module.checkout.service;

import com.meant.api.module.checkout.entity.CheckoutIdempotencyKey;
import com.meant.api.module.checkout.entity.CheckoutIdempotencyStatus;
import com.meant.api.module.checkout.exception.CheckoutSafetyException;
import com.meant.api.module.checkout.repository.CheckoutIdempotencyKeyRepository;
import com.meant.api.module.checkout.service.command.RecordIdempotencyResponseCommand;
import com.meant.api.module.checkout.service.command.ReserveIdempotencyKeyCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class IdempotencyKeyStore {

    private final CheckoutIdempotencyKeyRepository repository;
    private final TransactionOperations createTransaction;

    @Autowired
    public IdempotencyKeyStore(
            CheckoutIdempotencyKeyRepository repository,
            PlatformTransactionManager transactionManager
    ) {
        this(repository, requiresNewTransaction(transactionManager));
    }

    IdempotencyKeyStore(CheckoutIdempotencyKeyRepository repository) {
        this(repository, new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(null);
            }
        });
    }

    private IdempotencyKeyStore(
            CheckoutIdempotencyKeyRepository repository,
            TransactionOperations createTransaction
    ) {
        this.repository = repository;
        this.createTransaction = createTransaction;
    }

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
                .orElseThrow(() -> new CheckoutSafetyException(
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
            throw new CheckoutSafetyException("Unsupported idempotency response status: " + command.status());
        }
        return repository.save(record);
    }

    public String bodyHash(byte[] body) {
        return hash(body);
    }

    private CheckoutIdempotencyKey existingOrConflict(CheckoutIdempotencyKey existing, String bodyHash) {
        if (!existing.getBodyHash().equals(bodyHash)) {
            throw new CheckoutSafetyException("Idempotency key was reused with a different request body");
        }
        return existing;
    }

    private CheckoutIdempotencyKey create(ReserveIdempotencyKeyCommand command, String bodyHash) {
        try {
            return createTransaction.execute(status -> saveNew(command, bodyHash));
        } catch (DataIntegrityViolationException exception) {
            return repository.findByIdempotencyKey(command.idempotencyKey())
                    .map(existing -> existingOrConflict(existing, bodyHash))
                    .orElseThrow(() -> new CheckoutSafetyException(
                            "Idempotency key reservation collided but no existing record was found",
                            exception
                    ));
        }
    }

    private CheckoutIdempotencyKey saveNew(ReserveIdempotencyKeyCommand command, String bodyHash) {
        Instant now = Instant.now();
        return repository.saveAndFlush(CheckoutIdempotencyKey.builder()
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

    private static TransactionOperations requiresNewTransaction(PlatformTransactionManager transactionManager) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transactionTemplate;
    }

    private String hash(byte[] body) {
        try {
            byte[] safeBody = body == null ? new byte[0] : body;
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(safeBody));
        } catch (NoSuchAlgorithmException exception) {
            throw new CheckoutSafetyException("SHA-256 hash algorithm is unavailable", exception);
        }
    }

    public String stringHash(String value) {
        return hash(value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8));
    }
}
