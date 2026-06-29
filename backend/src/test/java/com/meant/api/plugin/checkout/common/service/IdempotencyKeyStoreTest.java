package com.meant.api.plugin.checkout.common.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.plugin.checkout.common.entity.CheckoutIdempotencyKey;
import com.meant.api.plugin.checkout.common.entity.CheckoutIdempotencyStatus;
import com.meant.api.plugin.checkout.common.exception.UcpCheckoutSafetyException;
import com.meant.api.plugin.checkout.common.repository.CheckoutIdempotencyKeyRepository;
import com.meant.api.plugin.checkout.common.service.command.RecordIdempotencyResponseCommand;
import com.meant.api.plugin.checkout.common.service.command.ReserveIdempotencyKeyCommand;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

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
                .isInstanceOf(UcpCheckoutSafetyException.class)
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

    private static final class FakeCheckoutIdempotencyKeyRepository {

        private final Map<String, CheckoutIdempotencyKey> records = new LinkedHashMap<>();
        private int saveCount;

        private CheckoutIdempotencyKeyRepository proxy() {
            return (CheckoutIdempotencyKeyRepository) Proxy.newProxyInstance(
                    CheckoutIdempotencyKeyRepository.class.getClassLoader(),
                    new Class<?>[]{CheckoutIdempotencyKeyRepository.class},
                    (proxy, method, args) -> {
                        String methodName = method.getName();
                        if ("findByIdempotencyKey".equals(methodName)) {
                            return Optional.ofNullable(records.get(args[0]));
                        }
                        if ("save".equals(methodName)) {
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
