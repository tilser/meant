package com.meant.api.plugin.checkout.update;

import com.meant.api.plugin.checkout.common.dto.CheckoutBuyer;
import com.meant.api.plugin.checkout.common.dto.CheckoutCapabilityMetadata;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
import com.meant.api.plugin.checkout.common.support.CheckoutPluginJson;
import com.meant.api.plugin.checkout.extension.buyerconsent.BuyerConsentExtensionSupport;
import com.meant.api.plugin.checkout.extension.buyerconsent.dto.BuyerWithConsent;
import com.meant.api.plugin.checkout.extension.discount.DiscountExtensionSupport;
import com.meant.api.plugin.checkout.extension.fulfillment.FulfillmentExtensionSupport;
import com.meant.api.plugin.checkout.update.dto.UpdateCheckoutArguments;
import com.meant.api.plugin.checkout.update.dto.UpdateCheckoutRequest;
import com.meant.api.plugin.spi.CapabilityAdvertisement;
import com.meant.api.plugin.spi.CapabilityId;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.plugin.spi.UcpCapability;
import com.meant.api.plugin.spi.UcpToolResponse;
import com.meant.api.plugin.support.UcpAttribution;
import com.meant.api.plugin.transport.profile.AgentAttributionProperties;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class UpdateCheckoutCapability implements UcpCapability<UpdateCheckoutRequest, UcpCheckoutResponse> {

    public static final String TOOL_NAME = "update_checkout";
    public static final CapabilityId ID = CapabilityId.of("dev.ucp.shopping.checkout.update");

    private final ObjectMapper objectMapper;
    private final UcpAttribution attribution;

    public UpdateCheckoutCapability(ObjectMapper objectMapper, AgentAttributionProperties attributionProperties) {
        this.objectMapper = objectMapper;
        this.attribution = attributionProperties.attribution();
    }

    @Override
    public CapabilityId id() {
        return ID;
    }

    @Override
    public List<CapabilityAdvertisement> advertisements() {
        return List.of(CheckoutCapabilityMetadata.required(TOOL_NAME));
    }

    @Override
    public UpdateCheckoutArguments buildArguments(
            UpdateCheckoutRequest request,
            NegotiatedCapabilities activeCapabilities
    ) {
        CheckoutBuyer buyerInput = request.buyer() == null
                ? new CheckoutBuyer(null, null, null, null)
                : request.buyer();
        buyerInput = buyerInput.withEmailIfMissing(request.email());
        BuyerWithConsent buyer = BuyerConsentExtensionSupport.buyer(
                buyerInput.empty() ? null : buyerInput,
                request.buyerConsent()
        );
        return new UpdateCheckoutArguments(
                request.checkoutId(),
                new UpdateCheckoutArguments.Checkout(
                        request.lineItems().stream()
                                .map(item -> new UpdateCheckoutArguments.LineItem(
                                        item.id(),
                                        new UpdateCheckoutArguments.Item(item.productVariantId()),
                                        item.quantity()
                                ))
                                .toList(),
                        buyer,
                        request.currency(),
                        request.context(),
                        FulfillmentExtensionSupport.fulfillment(request.fulfillment()),
                        DiscountExtensionSupport.replacementDiscountCodes(request.discountCodes()),
                        attribution
                )
        );
    }

    @Override
    public UcpCheckoutResponse parseResponse(UcpToolResponse response) {
        return CheckoutPluginJson.parse(objectMapper, response, UcpCheckoutResponse.class);
    }
}
