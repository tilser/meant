package com.meant.api.module.merchant.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HuggingFaceRowsResponse(
        List<HuggingFaceFeature> features,
        List<HuggingFaceDatasetRow> rows,
        @JsonProperty("num_rows_total") int numRowsTotal,
        @JsonProperty("num_rows_per_page") int numRowsPerPage,
        boolean partial
) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record HuggingFaceFeature(
        @JsonProperty("feature_idx") int featureIdx,
        String name,
        HuggingFaceFeatureType type
) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record HuggingFaceFeatureType(
        String dtype,
        @JsonProperty("_type") String type
) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record HuggingFaceDatasetRow(
        @JsonProperty("row_idx") int rowIdx,
        UcpMerchantDatasetRow row,
        @JsonProperty("truncated_cells") List<String> truncatedCells
) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record UcpMerchantDatasetRow(
        String domain,
        String status,
        @JsonProperty("ucp_url") String ucpUrl,
        @JsonProperty("http_status") Double httpStatus,
        String version,
        @JsonProperty("has_checkout") Integer hasCheckout,
        @JsonProperty("has_identity_linking") Integer hasIdentityLinking,
        @JsonProperty("has_cart_management") Integer hasCartManagement,
        @JsonProperty("has_order") Integer hasOrder,
        @JsonProperty("has_payment_token") Integer hasPaymentToken,
        @JsonProperty("capability_count") Integer capabilityCount,
        @JsonProperty("ai_bot_policies") String aiBotPolicies,
        String transports,
        @JsonProperty("last_checked_at") Instant lastCheckedAt,
        @JsonProperty("last_success_at") Instant lastSuccessAt
) {
}
