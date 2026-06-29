package com.meant.api.plugin.checkout.get;

import com.meant.api.plugin.checkout.common.dto.CheckoutCapabilityMetadata;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
import com.meant.api.plugin.checkout.common.support.CheckoutPluginJson;
import com.meant.api.plugin.checkout.get.dto.GetCheckoutArguments;
import com.meant.api.plugin.checkout.get.dto.GetCheckoutRequest;
import com.meant.api.plugin.spi.CapabilityAdvertisement;
import com.meant.api.plugin.spi.CapabilityId;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.plugin.spi.UcpCapability;
import com.meant.api.plugin.spi.UcpToolResponse;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class GetCheckoutCapability implements UcpCapability<GetCheckoutRequest, UcpCheckoutResponse> {

    public static final String TOOL_NAME = "get_checkout";
    public static final CapabilityId ID = CapabilityId.of("dev.ucp.shopping.checkout.get");

    private final ObjectMapper objectMapper;

    public GetCheckoutCapability(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public GetCheckoutCapability() {
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
    public GetCheckoutArguments buildArguments(GetCheckoutRequest request, NegotiatedCapabilities activeCapabilities) {
        return new GetCheckoutArguments(request.checkoutId());
    }

    @Override
    public UcpCheckoutResponse parseResponse(UcpToolResponse response) {
        return CheckoutPluginJson.parse(objectMapper, response, UcpCheckoutResponse.class);
    }
}
