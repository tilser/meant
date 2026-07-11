package com.meant.api.plugin.cart.update;

import com.meant.api.plugin.cart.common.dto.CartCapabilityMetadata;
import com.meant.api.plugin.cart.common.dto.CartToolArguments;
import com.meant.api.plugin.cart.common.dto.UcpCartResponse;
import com.meant.api.plugin.cart.common.support.CartPluginJson;
import com.meant.api.plugin.cart.update.dto.UpdateCartArguments;
import com.meant.api.plugin.cart.update.dto.UpdateCartRequest;
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
public class UpdateCartCapability implements UcpCapability<UpdateCartRequest, UcpCartResponse> {

    public static final String TOOL_NAME = "update_cart";
    public static final CapabilityId ID = CapabilityId.of("dev.ucp.shopping.cart.update");

    private final ObjectMapper objectMapper;

    public UpdateCartCapability(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
    public UpdateCartArguments buildArguments(
            UpdateCartRequest request,
            NegotiatedCapabilities activeCapabilities
    ) {
        return new UpdateCartArguments(
                request.cartId(),
                request.replacementState() == null ? CartToolArguments.update(
                        request.addItems(),
                        request.updateItems(),
                        request.removeItems(),
                        request.buyerIdentity(),
                        request.context(),
                        Map.of(),
                        request.deliveryAddressesToAdd(),
                        request.deliveryAddressesToReplace(),
                        request.selectedDeliveryOptions(),
                        request.discountCodes(),
                        request.giftCardCodes(),
                        request.note()
                ) : request.replacementState().arguments()
        );
    }

    @Override
    public UcpCartResponse parseResponse(UcpToolResponse response) {
        return CartPluginJson.parseCartResponse(objectMapper, response);
    }
}
