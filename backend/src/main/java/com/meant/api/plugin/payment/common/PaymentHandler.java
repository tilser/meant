package com.meant.api.plugin.payment.common;

import com.meant.api.plugin.payment.common.dto.PaymentBinding;
import com.meant.api.plugin.payment.common.dto.PaymentInstrument;
import java.util.List;

public interface PaymentHandler<TRequest, TResult> {

    String id();

    List<String> names();

    PaymentInstrument buildCredential(TRequest request);

    TResult parseResult(Object result, PaymentBinding expectedBinding);
}
