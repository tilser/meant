package com.meant.api.plugin.checkout.cancel;

import com.meant.api.plugin.checkout.cancel.dto.CancelCheckoutArguments;
import com.meant.api.plugin.checkout.cancel.dto.CancelCheckoutRequest;
import com.meant.api.plugin.checkout.common.dto.CheckoutCapabilityMetadata;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
import com.meant.api.plugin.checkout.common.support.CheckoutPluginJson;
import com.meant.api.plugin.spi.CapabilityAdvertisement;
import com.meant.api.plugin.spi.CapabilityId;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.plugin.spi.UcpCapability;
import com.meant.api.plugin.spi.UcpToolResponse;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class CancelCheckoutCapability implements UcpCapability<CancelCheckoutRequest, UcpCheckoutResponse> {

    public static final String TOOL_NAME = "cancel_checkout";
    public static final CapabilityId ID = CapabilityId.of("dev.ucp.shopping.checkout.cancel");

    private final ObjectMapper objectMapper;

    public CancelCheckoutCapability(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public CapabilityId id() {
        return ID;
    }

    @Override
    public List<CapabilityAdvertisement> advertisements() {
        return List.of(CheckoutCapabilityMetadata.required(TOOL_NAME));
    }

    @Override
    public CancelCheckoutArguments buildArguments(CancelCheckoutRequest request, NegotiatedCapabilities activeCapabilities) {
        return new CancelCheckoutArguments(request.checkoutId());
    }

    @Override
    public UcpCheckoutResponse parseResponse(UcpToolResponse response) {
        return CheckoutPluginJson.parse(objectMapper, response, UcpCheckoutResponse.class);
    }
}
