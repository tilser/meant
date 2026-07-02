package com.meant.api.plugin.checkout.create;

import com.meant.api.plugin.checkout.common.dto.CheckoutCapabilityMetadata;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
import com.meant.api.plugin.checkout.common.support.CheckoutPluginJson;
import com.meant.api.plugin.checkout.create.dto.CreateCheckoutArguments;
import com.meant.api.plugin.checkout.create.dto.CreateCheckoutRequest;
import com.meant.api.plugin.checkout.extension.buyerconsent.BuyerConsentExtensionSupport;
import com.meant.api.plugin.checkout.extension.discount.DiscountExtensionSupport;
import com.meant.api.plugin.checkout.extension.fulfillment.FulfillmentExtensionSupport;
import com.meant.api.plugin.spi.CapabilityAdvertisement;
import com.meant.api.plugin.spi.CapabilityId;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.plugin.spi.UcpCapability;
import com.meant.api.plugin.spi.UcpToolResponse;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class CreateCheckoutCapability implements UcpCapability<CreateCheckoutRequest, UcpCheckoutResponse> {

    public static final String TOOL_NAME = "create_checkout";
    public static final CapabilityId ID = CapabilityId.of("dev.ucp.shopping.checkout.create");

    private final ObjectMapper objectMapper;

    public CreateCheckoutCapability(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
    public CreateCheckoutArguments buildArguments(
            CreateCheckoutRequest request,
            NegotiatedCapabilities activeCapabilities
    ) {
        return new CreateCheckoutArguments(new CreateCheckoutArguments.Checkout(
                request.cartId(),
                request.lineItems().stream()
                        .filter(item -> item.productVariantId() != null && !item.productVariantId().isBlank())
                        .map(item -> new CreateCheckoutArguments.LineItem(
                                new CreateCheckoutArguments.Item(item.productVariantId()),
                                item.quantity()
                        ))
                        .toList(),
                BuyerConsentExtensionSupport.buyer(request.buyer(), request.buyerConsent()),
                DiscountExtensionSupport.discountCodes(request.discountCodes()),
                FulfillmentExtensionSupport.fulfillment(null, request.fulfillment())
        ));
    }

    @Override
    public UcpCheckoutResponse parseResponse(UcpToolResponse response) {
        return CheckoutPluginJson.parse(objectMapper, response, UcpCheckoutResponse.class);
    }
}
