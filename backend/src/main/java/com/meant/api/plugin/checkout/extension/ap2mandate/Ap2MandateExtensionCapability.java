package com.meant.api.plugin.checkout.extension.ap2mandate;

import com.meant.api.plugin.checkout.common.dto.CheckoutCapabilityMetadata;
import com.meant.api.plugin.spi.CapabilityAdvertisement;
import com.meant.api.plugin.spi.CapabilityId;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.plugin.spi.UcpCapability;
import com.meant.api.plugin.spi.UcpToolResponse;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class Ap2MandateExtensionCapability implements UcpCapability<Void, Void> {

    public static final CapabilityId ID = Ap2MandateExtensionSupport.ID;

    @Override
    public CapabilityId id() {
        return ID;
    }

    @Override
    public List<CapabilityAdvertisement> advertisements() {
        return List.of(CheckoutCapabilityMetadata.optionalExtension(
                ID,
                "ap2-mandates",
                "ap2_mandate.json",
                List.of(CheckoutCapabilityMetadata.CHECKOUT),
                Map.of("vp_formats_supported", Map.of("dc+sd-jwt", Map.of()))
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
