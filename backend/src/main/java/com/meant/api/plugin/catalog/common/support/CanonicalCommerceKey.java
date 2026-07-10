package com.meant.api.plugin.catalog.common.support;

import com.meant.api.plugin.catalog.common.dto.ExternalIdentifier;
import com.meant.api.plugin.catalog.common.dto.OfferIdentity;
import com.meant.api.plugin.catalog.common.dto.ProductIdentityEvidence;
import com.meant.api.plugin.catalog.common.dto.SellingPlanIdentity;
import com.meant.api.plugin.catalog.common.dto.SellingPlanOption;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** SHA-256 keys over length-prefixed, versioned identity fields. */
public final class CanonicalCommerceKey {

    private CanonicalCommerceKey() {
    }

    public static String offerKey(OfferIdentity identity) {
        List<String> fields = new ArrayList<>();
        fields.add(identity.provider().value());
        fields.add(identity.merchantIntegrationId().toString());
        addIdentifier(fields, identity.externalMerchantIdentity());
        addIdentifier(fields, identity.externalProductIdentity());
        addIdentifier(fields, identity.externalVariantIdentity());
        addSellingPlan(fields, identity.sellingPlanIdentity());
        return "offer_v1_" + digest("offer", fields);
    }

    public static String fallbackProductKey(OfferIdentity identity) {
        List<String> fields = new ArrayList<>();
        fields.add(identity.provider().value());
        fields.add(identity.merchantIntegrationId().toString());
        addIdentifier(fields, identity.externalMerchantIdentity());
        addIdentifier(fields, identity.externalProductIdentity());
        return "product_v1_" + digest("fallback-product", fields);
    }

    public static String evidenceGroupingKey(ProductIdentityEvidence evidence) {
        List<String> fields = evidenceFields(evidence);
        return "evidence_v1_" + digest("identity-evidence", fields);
    }

    public static String canonicalProductKey(ProductIdentityEvidence evidence) {
        return "product_v1_" + digest("canonical-product", evidenceFields(evidence));
    }

    private static List<String> evidenceFields(ProductIdentityEvidence evidence) {
        List<String> fields = new ArrayList<>();
        fields.add(evidence.kind().name());
        for (ExternalIdentifier identifier : evidence.identifiers()) {
            addIdentifier(fields, identifier);
        }
        return fields;
    }

    private static void addIdentifier(List<String> fields, ExternalIdentifier identifier) {
        if (identifier == null) {
            fields.add(null);
            return;
        }
        fields.add(identifier.type().name());
        fields.add(identifier.namespace());
        fields.add(identifier.value());
    }

    private static void addSellingPlan(List<String> fields, SellingPlanIdentity sellingPlan) {
        if (sellingPlan == null) {
            fields.add(null);
            return;
        }
        addIdentifier(fields, sellingPlan.groupReference());
        addIdentifier(fields, sellingPlan.planReference());
        fields.add(Integer.toString(sellingPlan.options().size()));
        for (SellingPlanOption option : sellingPlan.options()) {
            fields.add(option.name());
            fields.add(option.value());
        }
    }

    private static String digest(String domain, List<String> fields) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                writeField(output, domain);
                output.writeInt(1);
                output.writeInt(fields.size());
                for (String field : fields) {
                    writeField(output, field);
                }
            }
            return HexFormat.of().formatHex(digest.digest(bytes.toByteArray()));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not encode canonical commerce key", exception);
        }
    }

    private static void writeField(DataOutputStream output, String value) throws IOException {
        if (value == null) {
            output.writeInt(-1);
            return;
        }
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(encoded.length);
        output.write(encoded);
    }
}
