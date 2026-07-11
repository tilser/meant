package com.meant.api.module.checkout.service;

import com.meant.api.module.merchant.constant.CommerceOperation;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.module.checkout.entity.CheckoutCanaryOutcome;
import com.meant.api.module.checkout.service.CheckoutCanaryService.CanaryEventCommand;
import com.meant.api.module.checkout.service.dto.NativeCheckoutInterpretation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NativeCheckoutCanaryRecorder {

    private final CheckoutCanaryService checkoutCanaryService;

    public void record(MerchantCartProvider provider, NativeCheckoutInterpretation interpretation) {
        if (interpretation.canaryOutcome() == null) {
            return;
        }
        record(
                provider,
                interpretation.result().checkoutId(),
                interpretation.canaryOutcome(),
                interpretation.remoteStatus(),
                interpretation.errorCode(),
                interpretation.chargeMismatch()
        );
    }

    public void record(
            MerchantCartProvider provider,
            String checkoutId,
            CheckoutCanaryOutcome outcome,
            String remoteStatus,
            String errorCode,
            boolean chargeMismatch
    ) {
        checkoutCanaryService.record(new CanaryEventCommand(
                provider.merchantId(),
                checkoutId,
                outcome,
                remoteStatus,
                errorCode,
                chargeMismatch,
                provider.executionPolicy().isAvailable(CommerceOperation.DIRECT_CHECKOUT_COMPLETION)
        ));
    }
}
