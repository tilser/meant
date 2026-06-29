package com.meant.api.plugin.checkout.buyerconsent;

import com.meant.api.plugin.checkout.buyerconsent.dto.BuyerConsentArguments;
import com.meant.api.plugin.checkout.buyerconsent.dto.BuyerConsentRequest;
import com.meant.api.plugin.checkout.buyerconsent.dto.BuyerConsentResponse;
import com.meant.api.plugin.checkout.common.dto.CheckoutCapabilityMetadata;
import com.meant.api.plugin.checkout.common.support.CheckoutPluginJson;
import com.meant.api.plugin.spi.CapabilityAdvertisement;
import com.meant.api.plugin.spi.CapabilityId;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.plugin.spi.UcpCapability;
import com.meant.api.plugin.spi.UcpToolResponse;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class BuyerConsentCapability implements UcpCapability<BuyerConsentRequest, BuyerConsentResponse> {

    public static final String TOOL_NAME = "buyer_consent";
    public static final CapabilityId ID = CapabilityId.of("dev.ucp.shopping.buyer_consent");

    private final ObjectMapper objectMapper;

    public BuyerConsentCapability(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public BuyerConsentCapability() {
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
    public BuyerConsentArguments buildArguments(
            BuyerConsentRequest request,
            NegotiatedCapabilities activeCapabilities
    ) {
        return new BuyerConsentArguments(request.artifact());
    }

    @Override
    public BuyerConsentResponse parseResponse(UcpToolResponse response) {
        return CheckoutPluginJson.parse(objectMapper, response, BuyerConsentResponse.class);
    }
}
