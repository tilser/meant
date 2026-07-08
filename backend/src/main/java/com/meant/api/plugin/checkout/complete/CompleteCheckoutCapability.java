package com.meant.api.plugin.checkout.complete;

import com.meant.api.plugin.checkout.common.dto.CheckoutCapabilityMetadata;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
import com.meant.api.plugin.checkout.common.support.CheckoutPluginJson;
import com.meant.api.plugin.checkout.complete.dto.CompleteCheckoutArguments;
import com.meant.api.plugin.checkout.complete.dto.CompleteCheckoutRequest;
import com.meant.api.plugin.checkout.extension.ap2mandate.Ap2MandateExtensionSupport;
import com.meant.api.plugin.spi.CapabilityAdvertisement;
import com.meant.api.plugin.spi.CapabilityId;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.plugin.spi.UcpCapability;
import com.meant.api.plugin.spi.UcpToolResponse;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class CompleteCheckoutCapability implements UcpCapability<CompleteCheckoutRequest, UcpCheckoutResponse> {

    public static final String TOOL_NAME = "complete_checkout";
    public static final CapabilityId ID = CapabilityId.of("dev.ucp.shopping.checkout.complete");

    private final ObjectMapper objectMapper;

    public CompleteCheckoutCapability(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CompleteCheckoutCapability() {
        this(new ObjectMapper());
    }

    @Override
    public CapabilityId id() {
        return ID;
    }

    @Override
    public List<CapabilityAdvertisement> advertisements() {
        return List.of(CheckoutCapabilityMetadata.required(ID, TOOL_NAME));
    }

    @Override
    public CompleteCheckoutArguments buildArguments(
            CompleteCheckoutRequest request,
            NegotiatedCapabilities activeCapabilities
    ) {
        return new CompleteCheckoutArguments(
                request.checkoutId(),
                new CompleteCheckoutArguments.Checkout(
                        new CompleteCheckoutArguments.Payment(request.paymentInstruments()),
                        Ap2MandateExtensionSupport.completeRequest(request.checkoutMandate()),
                        request.signals()
                )
        );
    }

    @Override
    public UcpCheckoutResponse parseResponse(UcpToolResponse response) {
        return CheckoutPluginJson.parse(objectMapper, response, UcpCheckoutResponse.class);
    }

}
