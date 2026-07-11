package com.meant.api.module.checkout.service;

import com.meant.api.module.checkout.entity.CheckoutIdempotencyStatus;
import com.meant.api.module.checkout.service.command.RecordIdempotencyResponseCommand;
import com.meant.api.module.checkout.service.dto.NativeCheckoutInterpretation;
import com.meant.api.module.checkout.service.dto.NativeCheckoutResult;
import com.meant.api.module.checkout.service.dto.NativeCheckoutStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NativeCheckoutIdempotencyResponseRecorder {

    private final IdempotencyKeyStore idempotencyKeyStore;

    public void recordCompletionResponse(
            String idempotencyKey,
            NativeCheckoutInterpretation interpretation,
            String rawResponse
    ) {
        recordCompletionResponse(idempotencyKey, interpretation.result(), rawResponse);
    }

    public void recordCompletionResponse(
            String idempotencyKey,
            NativeCheckoutResult result,
            String rawResponse
    ) {
        CheckoutIdempotencyStatus status = idempotencyStatus(result.status());
        if (status == null) {
            return;
        }
        idempotencyKeyStore.recordResponse(new RecordIdempotencyResponseCommand(
                idempotencyKey,
                status,
                rawResponse,
                result.status() == NativeCheckoutStatus.COMPLETED ? result.orderRef() : null
        ));
    }

    private CheckoutIdempotencyStatus idempotencyStatus(NativeCheckoutStatus status) {
        return switch (status) {
            case COMPLETED -> CheckoutIdempotencyStatus.COMPLETED;
            case CANCELED, UNRECOVERABLE_ERROR -> CheckoutIdempotencyStatus.FAILED;
            case SCA_REQUIRED, RECOVERABLE_ERROR, PROCESSING -> CheckoutIdempotencyStatus.COMPLETION_IN_FLIGHT;
            default -> null;
        };
    }
}
