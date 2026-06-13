package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.meant.api.module.merchant.properties.CrawlingProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class UcpDatasetClientTest {

    @Test
    void fetchAllRowsUsesOffsetAndLengthPagination() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        CrawlingProperties properties = new CrawlingProperties(
                "https://datasets.example/rows?dataset=UCPChecker%2Fucp-merchants&config=default&split=train",
                2,
                "0 0 3 2 * *",
                "UTC"
        );
        server.expect(once(), requestTo("https://datasets.example/rows?dataset=UCPChecker%2Fucp-merchants&config=default&split=train&offset=0&length=2"))
                .andRespond(withSuccess(firstPage(), MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://datasets.example/rows?dataset=UCPChecker%2Fucp-merchants&config=default&split=train&offset=2&length=2"))
                .andRespond(withSuccess(secondPage(), MediaType.APPLICATION_JSON));
        UcpDatasetClient client = new UcpDatasetClient(restClientBuilder, properties);

        List<HuggingFaceDatasetRow> rows = client.fetchAllRows();

        assertThat(rows).hasSize(3);
        assertThat(rows.getFirst().row().domain()).isEqualTo("verified.example");
        assertThat(rows.getFirst().row().aiBotPolicies()).contains("GPTBot");
        assertThat(rows.getFirst().row().transports()).isEqualTo("[\"mcp\",\"embedded\"]");
        server.verify();
    }

    private String firstPage() {
        return """
                {
                  "features": [],
                  "rows": [
                    {
                      "row_idx": 89,
                      "row": {
                        "domain": "verified.example",
                        "status": "verified",
                        "ucp_url": "https://verified.example/.well-known/ucp",
                        "http_status": 200.0,
                        "version": "2026-01-23",
                        "has_checkout": 1,
                        "has_identity_linking": 0,
                        "has_cart_management": 0,
                        "has_order": 1,
                        "has_payment_token": 0,
                        "capability_count": 2,
                        "ai_bot_policies": "{\\"GPTBot\\": true, \\"ClaudeBot\\": false}",
                        "transports": "[\\"mcp\\",\\"embedded\\"]",
                        "last_checked_at": "2026-04-02T09:00:15+00:00",
                        "last_success_at": "2026-04-02T09:00:15+00:00"
                      },
                      "truncated_cells": []
                    },
                    {
                      "row_idx": 90,
                      "row": {
                        "domain": "pending.example",
                        "status": "pending",
                        "ucp_url": "https://pending.example/.well-known/ucp",
                        "http_status": 200.0,
                        "version": "2026-01-23",
                        "has_checkout": 0,
                        "has_identity_linking": 0,
                        "has_cart_management": 0,
                        "has_order": 0,
                        "has_payment_token": 0,
                        "capability_count": 0,
                        "ai_bot_policies": "{\\"GPTBot\\": false}",
                        "transports": "[]",
                        "last_checked_at": null,
                        "last_success_at": null
                      },
                      "truncated_cells": []
                    }
                  ],
                  "num_rows_total": 3,
                  "num_rows_per_page": 2,
                  "partial": false
                }
                """;
    }

    private String secondPage() {
        return """
                {
                  "features": [],
                  "rows": [
                    {
                      "row_idx": 91,
                      "row": {
                        "domain": "second-page.example",
                        "status": "verified",
                        "ucp_url": "https://second-page.example/.well-known/ucp",
                        "http_status": 200.0,
                        "version": "2026-01-23",
                        "has_checkout": 1,
                        "has_identity_linking": 1,
                        "has_cart_management": 1,
                        "has_order": 1,
                        "has_payment_token": 1,
                        "capability_count": 5,
                        "ai_bot_policies": "{\\"GPTBot\\": true}",
                        "transports": "[\\"mcp\\"]",
                        "last_checked_at": "2026-04-02T09:00:15+00:00",
                        "last_success_at": "2026-04-02T09:00:15+00:00"
                      },
                      "truncated_cells": []
                    }
                  ],
                  "num_rows_total": 3,
                  "num_rows_per_page": 1,
                  "partial": false
                }
                """;
    }
}
