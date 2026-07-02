package com.meant.api.plugin.checkout.extension.fulfillment;

import com.meant.api.plugin.checkout.common.dto.CheckoutCapabilityMetadata;
import com.meant.api.plugin.spi.CapabilityAdvertisement;
import com.meant.api.plugin.spi.CapabilityId;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.plugin.spi.UcpCapability;
import com.meant.api.plugin.spi.UcpToolResponse;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FulfillmentExtensionCapability implements UcpCapability<Void, Void> {

    public static final CapabilityId ID = FulfillmentExtensionSupport.ID;

    @Override
    public CapabilityId id() {
        return ID;
    }

    @Override
    public List<CapabilityAdvertisement> advertisements() {
        return List.of(CheckoutCapabilityMetadata.optionalExtension(
                ID,
                "fulfillment",
                "fulfillment.json",
                List.of(CheckoutCapabilityMetadata.CHECKOUT)
        ));
    }

    @Override
    public Object buildArguments(Void request, NegotiatedCapabilities activeCapabilities) {
        return null;
    }

    @Override
    public Void parseResponse(UcpToolResponse response) {
        return null;
    }
}
