package com.meant.api.plugin.cart.cancel;

import com.meant.api.plugin.cart.cancel.dto.CancelCartArguments;
import com.meant.api.plugin.cart.cancel.dto.CancelCartRequest;
import com.meant.api.plugin.cart.cancel.dto.CancelCartResponse;
import com.meant.api.plugin.cart.common.dto.CartCapabilityMetadata;
import com.meant.api.plugin.cart.common.support.CartPluginJson;
import com.meant.api.plugin.spi.CapabilityAdvertisement;
import com.meant.api.plugin.spi.CapabilityId;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.plugin.spi.UcpCapability;
import com.meant.api.plugin.spi.UcpToolResponse;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class CancelCartCapability implements UcpCapability<CancelCartRequest, CancelCartResponse> {

    public static final String TOOL_NAME = "cancel_cart";
    public static final CapabilityId ID = CapabilityId.of("dev.ucp.shopping.cart.cancel");

    private final ObjectMapper objectMapper;

    public CancelCartCapability(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CancelCartCapability() {
        this(new ObjectMapper());
    }

    @Override
    public CapabilityId id() {
        return ID;
    }

    @Override
    public List<CapabilityAdvertisement> advertisements() {
        return List.of(CartCapabilityMetadata.required(ID, TOOL_NAME));
    }

    @Override
    public CancelCartArguments buildArguments(
            CancelCartRequest request,
            NegotiatedCapabilities activeCapabilities
    ) {
        return new CancelCartArguments(request.cartId());
    }

    @Override
    public CancelCartResponse parseResponse(UcpToolResponse response) {
        return CartPluginJson.parse(objectMapper, response, CancelCartResponse.class);
    }
}
