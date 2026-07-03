package com.meant.api.module.review.service;

import com.meant.api.module.review.service.dto.ReviewProviderDetectionResult;
import com.meant.api.module.review.service.dto.StorefrontDocument;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ReviewProviderDetectionService {

    private static final int EVIDENCE_RADIUS = 60;
    private static final List<Pattern> KLAVIYO_ACCOUNT_PATTERNS = List.of(
            Pattern.compile("company_id=([A-Za-z0-9_-]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("static\\.klaviyo\\.com/onsite/js/([A-Za-z0-9_-]+)/klaviyo\\.js", Pattern.CASE_INSENSITIVE),
            Pattern.compile("klaviyo\\.init\\s*\\(\\s*\\{[^}]*account\\s*:\\s*[\"']([A-Za-z0-9_-]+)[\"']",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
            Pattern.compile("\"accountID\"\\s*:\\s*\"([A-Za-z0-9_-]+)\"", Pattern.CASE_INSENSITIVE)
    );
    private static final List<String> KLAVIYO_REVIEW_MARKERS = List.of(
            "klaviyo_reviews",
            "kl_reviews",
            "client_reviews",
            "MetafieldReviews",
            "klaviyoReviewsProductDesignMode",
            "reviews/api/client_reviews"
    );

    public ReviewProviderDetectionResult detect(String sourceUrl, String html) {
        return detect(List.of(new StorefrontDocument(sourceUrl, html)));
    }

    public ReviewProviderDetectionResult detect(List<StorefrontDocument> documents) {
        Optional<String> providerKey = documents.stream()
                .map(StorefrontDocument::html)
                .map(this::klaviyoAccountId)
                .flatMap(Optional::stream)
                .findFirst();
        Optional<MarkerEvidence> evidence = documents.stream()
                .map(this::klaviyoReviewEvidence)
                .flatMap(Optional::stream)
                .findFirst();

        if (providerKey.isPresent() && evidence.isPresent()) {
            MarkerEvidence markerEvidence = evidence.get();
            return ReviewProviderDetectionResult.klaviyo(
                    providerKey.get(),
                    markerEvidence.sourceUrl(),
                    markerEvidence.evidence()
            );
        }
        return ReviewProviderDetectionResult.notFound();
    }

    private Optional<String> klaviyoAccountId(String html) {
        if (html == null || html.isBlank()) {
            return Optional.empty();
        }
        for (Pattern pattern : KLAVIYO_ACCOUNT_PATTERNS) {
            Matcher matcher = pattern.matcher(html);
            if (matcher.find() && hasText(matcher.group(1))) {
                return Optional.of(matcher.group(1).trim());
            }
        }
        return Optional.empty();
    }

    private Optional<MarkerEvidence> klaviyoReviewEvidence(StorefrontDocument document) {
        String html = document.html();
        if (html == null || html.isBlank()) {
            return Optional.empty();
        }
        String lowerHtml = html.toLowerCase(Locale.ROOT);
        for (String marker : KLAVIYO_REVIEW_MARKERS) {
            int index = lowerHtml.indexOf(marker.toLowerCase(Locale.ROOT));
            if (index >= 0) {
                return Optional.of(new MarkerEvidence(document.sourceUrl(), snippet(html, index)));
            }
        }
        return Optional.empty();
    }

    private String snippet(String html, int markerIndex) {
        int start = Math.max(0, markerIndex - EVIDENCE_RADIUS);
        int end = Math.min(html.length(), markerIndex + EVIDENCE_RADIUS);
        return html.substring(start, end)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record MarkerEvidence(
            String sourceUrl,
            String evidence
    ) {
    }
}
