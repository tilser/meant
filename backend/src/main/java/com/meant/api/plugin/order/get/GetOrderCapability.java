package com.meant.api.plugin.order.get;

import com.meant.api.plugin.order.common.dto.OrderCapabilityMetadata;
import com.meant.api.plugin.order.common.dto.UcpOrderResponse;
import com.meant.api.plugin.order.common.support.OrderPluginJson;
import com.meant.api.plugin.order.get.dto.GetOrderArguments;
import com.meant.api.plugin.order.get.dto.GetOrderRequest;
import com.meant.api.plugin.spi.CapabilityAdvertisement;
import com.meant.api.plugin.spi.CapabilityId;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.plugin.spi.UcpCapability;
import com.meant.api.plugin.spi.UcpToolResponse;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class GetOrderCapability implements UcpCapability<GetOrderRequest, UcpOrderResponse> {

    public static final String TOOL_NAME = "get_order";
    public static final CapabilityId ID = CapabilityId.of("dev.ucp.shopping.order.get");

    private final ObjectMapper objectMapper;

    public GetOrderCapability(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public CapabilityId id() {
        return ID;
    }

    @Override
    public List<CapabilityAdvertisement> advertisements() {
        return List.of(OrderCapabilityMetadata.required(ID, TOOL_NAME));
    }

    @Override
    public GetOrderArguments buildArguments(GetOrderRequest request, NegotiatedCapabilities activeCapabilities) {
        return new GetOrderArguments(request.orderId());
    }

    @Override
    public UcpOrderResponse parseResponse(UcpToolResponse response) {
        return OrderPluginJson.parse(objectMapper, response, UcpOrderResponse.class);
    }
}
