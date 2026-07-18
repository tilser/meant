package com.meant.api.module.cart.controller.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.cart.controller.request.CartCreateRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CartCommandMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mapsFrontendCamelCaseCartFieldsIntoTypedCommandInputs() throws Exception {
        CartCreateRequest request = objectMapper.readValue(
                """
                        {
                          "addItems": [{"offerKey": "offer-1", "quantity": 1}],
                          "buyerIdentity": {
                            "email": "buyer@example.test",
                            "phoneNumber": "+14155552671",
                            "firstName": "Ada",
                            "lastName": "Lovelace",
                            "countryCode": "US"
                          },
                          "deliveryAddressesToAdd": [
                            {
                              "methodId": "shipping-1",
                              "id": "home",
                              "selected": true,
                              "streetAddress": "1 Main St",
                              "city": "New York",
                              "provinceCode": "NY",
                              "postalCode": "10001",
                              "countryCode": "US"
                            }
                          ],
                          "selectedDeliveryOptions": [
                            {
                              "methodId": "shipping-1",
                              "deliveryGroupId": "delivery-1",
                              "deliveryOptionHandle": "express"
                            }
                          ],
                          "discountCodes": [],
                          "giftCardCodes": []
                        }
                        """,
                CartCreateRequest.class);

        var command = CartCommandMapper.toCommand(UUID.randomUUID(), request);

        assertThat(command.buyerIdentity().email()).isEqualTo("buyer@example.test");
        assertThat(command.buyerIdentity().phoneNumber()).isEqualTo("+14155552671");
        assertThat(command.buyerIdentity().countryCode()).isEqualTo("US");
        assertThat(command.deliveryAddressesToAdd()).singleElement().satisfies(selection -> {
            assertThat(selection.methodId()).isEqualTo("shipping-1");
            assertThat(selection.id()).isEqualTo("home");
            assertThat(selection.selected()).isTrue();
            assertThat(selection.address().streetAddress()).isEqualTo("1 Main St");
            assertThat(selection.address().addressLocality()).isEqualTo("New York");
            assertThat(selection.address().addressRegion()).isEqualTo("NY");
            assertThat(selection.address().postalCode()).isEqualTo("10001");
            assertThat(selection.address().addressCountry()).isEqualTo("US");
        });
        assertThat(command.selectedDeliveryOptions()).singleElement().satisfies(selection -> {
            assertThat(selection.methodId()).isEqualTo("shipping-1");
            assertThat(selection.groupId()).isEqualTo("delivery-1");
            assertThat(selection.selectedOptionId()).isEqualTo("express");
        });
    }

    @Test
    void acceptsSnakeCaseWireAliasesAndGivesNestedDeliveryAddressPrecedence() throws Exception {
        CartCreateRequest request = objectMapper.readValue(
                """
                        {
                          "addItems": [{"offerKey": "offer-1", "quantity": 1}],
                          "buyerIdentity": {
                            "phone_number": "+14155552671",
                            "first_name": "Ada",
                            "last_name": "Lovelace",
                            "country_code": "US"
                          },
                          "deliveryAddressesToAdd": [
                            {
                              "fulfillment_method_id": "shipping-1",
                              "destination_id": "home",
                              "postal_code": "ignored-flat-value",
                              "delivery_address": {
                                "street_address": "1 Main St",
                                "address_locality": "New York",
                                "address_region": "NY",
                                "postal_code": "10001",
                                "address_country": "US"
                              }
                            }
                          ],
                          "selectedDeliveryOptions": [
                            {
                              "fulfillment_method_id": "shipping-1",
                              "delivery_group_id": "delivery-1",
                              "selected_option_id": "express"
                            }
                          ],
                          "discountCodes": [],
                          "giftCardCodes": []
                        }
                        """,
                CartCreateRequest.class);

        var command = CartCommandMapper.toCommand(UUID.randomUUID(), request);

        assertThat(command.buyerIdentity().firstName()).isEqualTo("Ada");
        assertThat(command.deliveryAddressesToAdd().getFirst().address().postalCode()).isEqualTo("10001");
        assertThat(command.selectedDeliveryOptions().getFirst().selectedOptionId()).isEqualTo("express");
    }
}
