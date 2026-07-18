package com.meant.api.plugin.checkout.extension.ap2mandate;

import com.meant.api.plugin.checkout.common.dto.CheckoutCapabilityMetadata;
import com.meant.api.plugin.spi.CapabilityAdvertisement;
import com.meant.api.plugin.spi.CapabilityId;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.plugin.spi.UcpCapability;
import com.meant.api.plugin.spi.UcpToolResponse;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

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
                config()
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

    private JsonNode config() {
        var formats = JsonNodeFactory.instance.objectNode();
        formats.set("dc+sd-jwt", JsonNodeFactory.instance.objectNode());
        var config = JsonNodeFactory.instance.objectNode();
        config.set("vp_formats_supported", formats);
        return config;
    }
}
