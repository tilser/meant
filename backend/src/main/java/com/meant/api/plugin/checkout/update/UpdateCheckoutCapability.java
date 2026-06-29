package com.meant.api.plugin.checkout.update;

import com.meant.api.plugin.checkout.common.dto.CheckoutCapabilityMetadata;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
import com.meant.api.plugin.checkout.common.support.CheckoutPluginJson;
import com.meant.api.plugin.checkout.update.dto.UpdateCheckoutArguments;
import com.meant.api.plugin.checkout.update.dto.UpdateCheckoutRequest;
import com.meant.api.plugin.spi.CapabilityAdvertisement;
import com.meant.api.plugin.spi.CapabilityId;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.plugin.spi.UcpCapability;
import com.meant.api.plugin.spi.UcpToolResponse;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class UpdateCheckoutCapability implements UcpCapability<UpdateCheckoutRequest, UcpCheckoutResponse> {

    public static final String TOOL_NAME = "update_checkout";
    public static final CapabilityId ID = CapabilityId.of("dev.ucp.shopping.checkout.update");

    private final ObjectMapper objectMapper;

    public UpdateCheckoutCapability(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public UpdateCheckoutCapability() {
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
    public UpdateCheckoutArguments buildArguments(
            UpdateCheckoutRequest request,
            NegotiatedCapabilities activeCapabilities
    ) {
        return new UpdateCheckoutArguments(
                request.checkoutId(),
                request.buyer(),
                request.email(),
                fulfillment(request.shippingAddress())
        );
    }

    @Override
    public UcpCheckoutResponse parseResponse(UcpToolResponse response) {
        return CheckoutPluginJson.parse(objectMapper, response, UcpCheckoutResponse.class);
    }

    private UpdateCheckoutArguments.Fulfillment fulfillment(Map<String, Object> shippingAddress) {
        return shippingAddress == null || shippingAddress.isEmpty()
                ? null
                : new UpdateCheckoutArguments.Fulfillment(shippingAddress);
    }
}
