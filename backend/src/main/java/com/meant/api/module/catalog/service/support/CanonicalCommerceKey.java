package com.meant.api.module.catalog.service.support;

import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.OfferIdentity;
import com.meant.api.module.catalog.service.dto.OfferComponentIdentity;
import com.meant.api.module.catalog.service.dto.OfferMerchantScope;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import com.meant.api.module.catalog.service.dto.ProductIdentityEvidence;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.dto.ResultSourceReference;
import com.meant.api.module.catalog.service.dto.SellingPlanIdentity;
import com.meant.api.module.catalog.service.dto.SellingPlanOption;
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
        addMerchantScopeV2(fields, identity.merchantScope());
        addIdentifierV2(fields, identity.externalProductIdentity());
        addIdentifierV2(fields, identity.externalVariantIdentity());
        addAttributesV2(fields, identity.selectedOptions());
        addComponentsV2(fields, identity.components());
        addSellingPlanV2(fields, identity.sellingPlanIdentity());
        return "offer_v2_" + digest("offer-v2", fields);
    }

    public static String fallbackProductKey(OfferIdentity identity) {
        List<String> fields = new ArrayList<>();
        fields.add(identity.provider().value());
        addMerchantScopeV2(fields, identity.merchantScope());
        addIdentifierV2(fields, identity.externalProductIdentity());
        return "product_v2_" + digest("fallback-product-v2", fields);
    }

    public static String evidenceGroupingKey(ProductIdentityEvidence evidence) {
        List<String> fields = evidenceFields(evidence);
        return "evidence_v1_" + digest("identity-evidence", fields);
    }

    public static String canonicalProductKey(ProductIdentityEvidence evidence) {
        return "product_v1_" + digest("canonical-product", evidenceFields(evidence));
    }

    public static String groupedProductKey(
            String identityKind,
            String normalizedIdentity,
            String compatibilityFingerprint
    ) {
        return "product_v3_" + digest(
                "grouped-product-v3",
                List.of(identityKind, normalizedIdentity, compatibilityFingerprint)
        );
    }

    public static String clusteredProductKey(List<String> stableMemberIdentities) {
        return "product_v3_" + digest(
                "clustered-product-v3",
                stableMemberIdentities.stream().sorted().distinct().toList()
        );
    }

    public static String merchantScopeKey(OfferMerchantScope merchantScope) {
        List<String> fields = new ArrayList<>();
        addMerchantScopeV2(fields, merchantScope);
        return digest("merchant-scope-v1", fields);
    }

    public static String upidAuthorityScopeKey(
            ProviderIdentity provider,
            ResultSourceReference sourceReference
    ) {
        return digest(
                "upid-authority-scope-v1",
                List.of(provider.value(), sourceReference.type().name(), sourceReference.reference())
        );
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

    private static void addMerchantScopeV2(List<String> fields, OfferMerchantScope merchantScope) {
        fields.add("merchant-scope");
        fields.add(merchantScope.type().name());
        if (merchantScope.externalMerchantIdentity() != null) {
            addIdentifierV2(fields, merchantScope.externalMerchantIdentity());
        } else {
            fields.add(merchantScope.merchantIntegrationFallbackId().toString());
        }
    }

    private static void addIdentifierV2(List<String> fields, ExternalIdentifier identifier) {
        fields.add("identifier");
        if (identifier == null) {
            fields.add(null);
            return;
        }
        fields.add(identifier.type().name());
        fields.add(identifier.namespace());
        fields.add(identifier.value());
    }

    private static void addAttributesV2(List<String> fields, List<ProductAttribute> attributes) {
        fields.add("attributes");
        fields.add(Integer.toString(attributes.size()));
        for (ProductAttribute attribute : attributes) {
            fields.add(attribute.group());
            fields.add(attribute.name());
            fields.add(attribute.value());
        }
    }

    private static void addComponentsV2(List<String> fields, List<OfferComponentIdentity> components) {
        fields.add("components");
        fields.add(Integer.toString(components.size()));
        for (OfferComponentIdentity component : components) {
            addIdentifierV2(fields, component.externalProductIdentity());
            addIdentifierV2(fields, component.externalVariantIdentity());
            fields.add(Integer.toString(component.quantity()));
            addAttributesV2(fields, component.selectedOptions());
        }
    }

    private static void addSellingPlanV2(List<String> fields, SellingPlanIdentity sellingPlan) {
        fields.add("selling-plan");
        if (sellingPlan == null) {
            fields.add(null);
            return;
        }
        addIdentifierV2(fields, sellingPlan.groupReference());
        addIdentifierV2(fields, sellingPlan.planReference());
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
