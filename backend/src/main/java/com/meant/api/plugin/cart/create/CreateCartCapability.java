package com.meant.api.plugin.cart.create;

import com.meant.api.plugin.cart.common.dto.CartCapabilityMetadata;
import com.meant.api.plugin.cart.common.dto.UcpCartResponse;
import com.meant.api.plugin.cart.common.support.CartPluginJson;
import com.meant.api.plugin.cart.create.dto.CreateCartArguments;
import com.meant.api.plugin.cart.create.dto.CreateCartRequest;
import com.meant.api.plugin.spi.CapabilityAdvertisement;
import com.meant.api.plugin.spi.CapabilityId;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.plugin.spi.UcpCapability;
import com.meant.api.plugin.spi.UcpToolResponse;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class CreateCartCapability implements UcpCapability<CreateCartRequest, UcpCartResponse> {

    public static final String TOOL_NAME = "create_cart";
    public static final CapabilityId ID = CapabilityId.of("dev.ucp.shopping.cart.create");

    private final ObjectMapper objectMapper;

    public CreateCartCapability(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CreateCartCapability() {
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
    public CreateCartArguments buildArguments(
            CreateCartRequest request,
            NegotiatedCapabilities activeCapabilities
    ) {
        return new CreateCartArguments(
                request.addItems(),
                request.buyerIdentity(),
                request.deliveryAddressesToAdd(),
                request.deliveryAddressesToReplace(),
                request.selectedDeliveryOptions(),
                request.discountCodes(),
                request.giftCardCodes(),
                request.note()
        );
    }

    @Override
    public UcpCartResponse parseResponse(UcpToolResponse response) {
        return CartPluginJson.parse(objectMapper, response, UcpCartResponse.class);
    }
}
