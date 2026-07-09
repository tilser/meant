package com.meant.api.plugin.checkout.update;

import com.meant.api.plugin.checkout.common.dto.CheckoutCapabilityMetadata;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
import com.meant.api.plugin.checkout.common.support.CheckoutPluginJson;
import com.meant.api.plugin.checkout.extension.buyerconsent.BuyerConsentExtensionSupport;
import com.meant.api.plugin.checkout.extension.discount.DiscountExtensionSupport;
import com.meant.api.plugin.checkout.extension.fulfillment.FulfillmentExtensionSupport;
import com.meant.api.plugin.checkout.update.dto.UpdateCheckoutArguments;
import com.meant.api.plugin.checkout.update.dto.UpdateCheckoutRequest;
import com.meant.api.plugin.spi.CapabilityAdvertisement;
import com.meant.api.plugin.spi.CapabilityId;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.plugin.spi.UcpCapability;
import com.meant.api.plugin.spi.UcpToolResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class UpdateCheckoutCapability implements UcpCapability<UpdateCheckoutRequest, UcpCheckoutResponse> {

    public static final String TOOL_NAME = "update_checkout";
    public static final CapabilityId ID = CapabilityId.of("dev.ucp.shopping.checkout.update");

    private final ObjectMapper objectMapper;

    public UpdateCheckoutCapability(ObjectMapper objectMapper) {
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
    public UpdateCheckoutArguments buildArguments(
            UpdateCheckoutRequest request,
            NegotiatedCapabilities activeCapabilities
    ) {
        Map<String, Object> buyer = BuyerConsentExtensionSupport.buyer(request.buyer(), request.buyerConsent());
        if (buyer == null) {
            buyer = new LinkedHashMap<>();
        }
        if (hasText(request.email()) && !buyer.containsKey("email")) {
            buyer.put("email", request.email().trim());
        }
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
                        DiscountExtensionSupport.discountCodes(request.discountCodes())
                )
        );
    }

    @Override
    public UcpCheckoutResponse parseResponse(UcpToolResponse response) {
        return CheckoutPluginJson.parse(objectMapper, response, UcpCheckoutResponse.class);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
