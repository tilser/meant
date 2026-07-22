package com.meant.api.plugin.cart.get;

import com.meant.api.plugin.cart.common.dto.CartCapabilityMetadata;
import com.meant.api.plugin.cart.common.dto.UcpCartResponse;
import com.meant.api.plugin.cart.common.support.CartPluginJson;
import com.meant.api.plugin.cart.get.dto.GetCartArguments;
import com.meant.api.plugin.cart.get.dto.GetCartRequest;
import com.meant.api.plugin.spi.CapabilityAdvertisement;
import com.meant.api.plugin.spi.CapabilityId;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.plugin.spi.UcpCapability;
import com.meant.api.plugin.spi.UcpToolResponse;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class GetCartCapability implements UcpCapability<GetCartRequest, UcpCartResponse> {

    public static final String TOOL_NAME = "get_cart";
    public static final CapabilityId ID = CapabilityId.of("dev.ucp.shopping.cart.get");

    private final ObjectMapper objectMapper;

    public GetCartCapability(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public CapabilityId id() {
        return ID;
    }

    @Override
    public List<CapabilityAdvertisement> advertisements() {
        return List.of(CartCapabilityMetadata.required(TOOL_NAME));
    }

    @Override
    public GetCartArguments buildArguments(GetCartRequest request, NegotiatedCapabilities activeCapabilities) {
        return new GetCartArguments(request.cartId());
    }

    @Override
    public UcpCartResponse parseResponse(UcpToolResponse response) {
        return CartPluginJson.parseCartResponse(objectMapper, response);
    }
}
