package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.exception.MerchantEnrichmentException;
import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class MerchantDomainNormalizer {

    public String normalizeDomain(String value) {
        if (value == null || value.isBlank()) {
            throw new MerchantEnrichmentException("Merchant domain is blank");
        }
        String candidate = value.trim();
        if (candidate.contains("://")) {
            candidate = host(candidate);
        } else {
            int slash = candidate.indexOf('/');
            if (slash >= 0) {
                candidate = candidate.substring(0, slash);
            }
        }
        while (candidate.endsWith(".")) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }
        try {
            String normalized = IDN.toASCII(candidate, IDN.USE_STD3_ASCII_RULES)
                    .toLowerCase(Locale.ROOT);
            if (normalized.startsWith("www.")) {
                normalized = normalized.substring("www.".length());
            }
            if (normalized.isBlank()) {
                throw new MerchantEnrichmentException("Merchant domain is blank");
            }
            return normalized;
        } catch (IllegalArgumentException exception) {
            throw new MerchantEnrichmentException("Merchant domain is invalid", exception);
        }
    }

    private String host(String value) {
        try {
            URI uri = new URI(value);
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new MerchantEnrichmentException("Merchant origin has no host");
            }
            return uri.getHost();
        } catch (URISyntaxException exception) {
            throw new MerchantEnrichmentException("Merchant origin is invalid", exception);
        }
    }
}
