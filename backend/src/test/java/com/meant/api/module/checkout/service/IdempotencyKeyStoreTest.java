package com.meant.api.module.checkout.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.module.checkout.entity.CheckoutIdempotencyKey;
import com.meant.api.module.checkout.entity.CheckoutIdempotencyStatus;
import com.meant.api.module.checkout.exception.CheckoutSafetyException;
import com.meant.api.module.checkout.repository.CheckoutIdempotencyKeyRepository;
import com.meant.api.module.checkout.service.command.RecordIdempotencyResponseCommand;
import com.meant.api.module.checkout.service.command.ReserveIdempotencyKeyCommand;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class IdempotencyKeyStoreTest {

    private final FakeCheckoutIdempotencyKeyRepository repository = new FakeCheckoutIdempotencyKeyRepository();
    private final IdempotencyKeyStore store = new IdempotencyKeyStore(repository.proxy());

    @Test
    void sameKeyAndSameBodyReturnsExistingRecordWithoutDoubleReserve() {
        UUID consentId = UUID.randomUUID();
        ReserveIdempotencyKeyCommand command = reserveCommand("idem-1", "{\"total\":1999}", consentId);

        CheckoutIdempotencyKey first = store.reserve(command);
        CheckoutIdempotencyKey retry = store.reserve(command);

        assertThat(retry.getId()).isEqualTo(first.getId());
        assertThat(retry.getBodyHash()).isEqualTo(first.getBodyHash());
        assertThat(repository.saveCount).isEqualTo(1);
        assertThat(repository.records).hasSize(1);
    }

    @Test
    void sameKeyWithDifferentBodyHardFails() {
        UUID consentId = UUID.randomUUID();
        store.reserve(reserveCommand("idem-1", "{\"total\":1999}", consentId));

        assertThatThrownBy(() -> store.reserve(reserveCommand("idem-1", "{\"total\":2999}", consentId)))
                .isInstanceOf(CheckoutSafetyException.class)
                .hasMessageContaining("different request body");
        assertThat(repository.saveCount).isEqualTo(1);
    }

    @Test
    void distinctIntentsUseDistinctKeys() {
        UUID consentId = UUID.randomUUID();

        CheckoutIdempotencyKey first = store.reserve(reserveCommand("idem-1", "{\"total\":1999}", consentId));
        CheckoutIdempotencyKey second = store.reserve(reserveCommand("idem-2", "{\"total\":1999}", consentId));

        assertThat(first.getIdempotencyKey()).isNotEqualTo(second.getIdempotencyKey());
        assertThat(repository.records).hasSize(2);
    }

    @Test
    void concurrentCreateRaceReturnsExistingRecordWhenBodyMatches() {
        UUID consentId = UUID.randomUUID();
        ReserveIdempotencyKeyCommand command = reserveCommand("idem-1", "{\"total\":1999}", consentId);
        CheckoutIdempotencyKey concurrentRecord = existingRecord(command, store.bodyHash(command.body()));
        repository.concurrentRecord = concurrentRecord;
        repository.throwDataIntegrityOnNextSave = true;

        CheckoutIdempotencyKey reserved = store.reserve(command);

        assertThat(reserved.getId()).isEqualTo(concurrentRecord.getId());
        assertThat(reserved.getBodyHash()).isEqualTo(concurrentRecord.getBodyHash());
    }

    @Test
    void concurrentCreateRaceWithDifferentBodyHardFailsCleanly() {
        UUID consentId = UUID.randomUUID();
        ReserveIdempotencyKeyCommand command = reserveCommand("idem-1", "{\"total\":1999}", consentId);
        repository.concurrentRecord = existingRecord(command, store.bodyHash("{\"total\":2999}".getBytes(StandardCharsets.UTF_8)));
        repository.throwDataIntegrityOnNextSave = true;

        assertThatThrownBy(() -> store.reserve(command))
                .isInstanceOf(CheckoutSafetyException.class)
                .hasMessageContaining("different request body");
    }

    @Test
    void recordsRemoteResponseAndFinalOrderReference() {
        UUID consentId = UUID.randomUUID();
        store.reserve(reserveCommand("idem-1", "{\"total\":1999}", consentId));

        CheckoutIdempotencyKey completed = store.recordResponse(new RecordIdempotencyResponseCommand(
                "idem-1",
                CheckoutIdempotencyStatus.COMPLETED,
                "{\"status\":\"completed\"}",
                "order-123"
        ));

        assertThat(completed.getStatus()).isEqualTo(CheckoutIdempotencyStatus.COMPLETED);
        assertThat(completed.getRemoteResponse()).isEqualTo("{\"status\":\"completed\"}");
        assertThat(completed.getFinalOrderRef()).isEqualTo("order-123");
    }

    private ReserveIdempotencyKeyCommand reserveCommand(String key, String body, UUID consentId) {
        return new ReserveIdempotencyKeyCommand(
                key,
                body.getBytes(StandardCharsets.UTF_8),
                "co_123",
                consentId,
                1999L,
                "usd",
                UUID.fromString("00000000-0000-0000-0000-000000000001")
        );
    }

    private CheckoutIdempotencyKey existingRecord(ReserveIdempotencyKeyCommand command, String bodyHash) {
        return CheckoutIdempotencyKey.builder()
                .idempotencyKey(command.idempotencyKey())
                .bodyHash(bodyHash)
                .checkoutId(command.checkoutId())
                .consentId(command.consentId())
                .amount(command.amount())
                .currency(command.currency().toUpperCase(java.util.Locale.ROOT))
                .merchantId(command.merchantId())
                .status(CheckoutIdempotencyStatus.RESERVED)
                .createdAt(java.time.Instant.now())
                .updatedAt(java.time.Instant.now())
                .build();
    }

    private static final class FakeCheckoutIdempotencyKeyRepository {

        private final Map<String, CheckoutIdempotencyKey> records = new LinkedHashMap<>();
        private int saveCount;
        private boolean throwDataIntegrityOnNextSave;
        private CheckoutIdempotencyKey concurrentRecord;

        private CheckoutIdempotencyKeyRepository proxy() {
            return (CheckoutIdempotencyKeyRepository) Proxy.newProxyInstance(
                    CheckoutIdempotencyKeyRepository.class.getClassLoader(),
                    new Class<?>[]{CheckoutIdempotencyKeyRepository.class},
                    (proxy, method, args) -> {
                        String methodName = method.getName();
                        if ("findByIdempotencyKey".equals(methodName)) {
                            return Optional.ofNullable(records.get(args[0]));
                        }
                        if ("save".equals(methodName) || "saveAndFlush".equals(methodName)) {
                            if (throwDataIntegrityOnNextSave) {
                                throwDataIntegrityOnNextSave = false;
                                records.put(concurrentRecord.getIdempotencyKey(), concurrentRecord);
                                throw new DataIntegrityViolationException("duplicate idempotency key");
                            }
                            CheckoutIdempotencyKey record = (CheckoutIdempotencyKey) args[0];
                            records.put(record.getIdempotencyKey(), record);
                            saveCount++;
                            return record;
                        }
                        if ("toString".equals(methodName)) {
                            return "FakeCheckoutIdempotencyKeyRepository";
                        }
                        throw new UnsupportedOperationException(methodName);
                    }
            );
        }
    }
}
