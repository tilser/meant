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
    private static final List<Pattern> YOTPO_ACCOUNT_PATTERNS = List.of(
            Pattern.compile("staticw2\\.yotpo\\.com/([A-Za-z0-9_-]+)/widget\\.js", Pattern.CASE_INSENSITIVE),
            Pattern.compile("cdn-widgetsrepository\\.yotpo\\.com/v1/loader/([A-Za-z0-9_-]+)",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\\\?\"yotpoStoreId\\\\?\"\\s*:\\s*\\\\?\"([A-Za-z0-9_-]+)\\\\?\"",
                    Pattern.CASE_INSENSITIVE)
    );
    private static final List<String> KLAVIYO_REVIEW_MARKERS = List.of(
            "klaviyo_reviews",
            "kl_reviews",
            "client_reviews",
            "reviews/api/client_reviews"
    );
    private static final List<String> YOTPO_REVIEW_MARKERS = List.of(
            "staticw2.yotpo.com",
            "yotpoStoreId",
            "MetafieldYotpoRating",
            "MetafieldYotpoCount",
            "data-yotpo-product-id",
            "yotpo-reviews-section-data",
            "yotpo-widget",
            "yotpo-main-widget"
    );

    public ReviewProviderDetectionResult detect(String sourceUrl, String html) {
        return detect(List.of(new StorefrontDocument(sourceUrl, html)));
    }

    public ReviewProviderDetectionResult detect(List<StorefrontDocument> documents) {
        Optional<String> yotpoProviderKey = documents.stream()
                .map(StorefrontDocument::html)
                .map(html -> providerKey(html, YOTPO_ACCOUNT_PATTERNS))
                .flatMap(Optional::stream)
                .findFirst();
        Optional<MarkerEvidence> yotpoEvidence = documents.stream()
                .map(document -> reviewEvidence(document, YOTPO_REVIEW_MARKERS))
                .flatMap(Optional::stream)
                .findFirst();

        if (yotpoProviderKey.isPresent() && yotpoEvidence.isPresent()) {
            MarkerEvidence markerEvidence = yotpoEvidence.get();
            return ReviewProviderDetectionResult.yotpo(
                    yotpoProviderKey.get(),
                    markerEvidence.sourceUrl(),
                    markerEvidence.evidence()
            );
        }

        Optional<String> providerKey = documents.stream()
                .map(StorefrontDocument::html)
                .map(html -> providerKey(html, KLAVIYO_ACCOUNT_PATTERNS))
                .flatMap(Optional::stream)
                .findFirst();
        Optional<MarkerEvidence> evidence = documents.stream()
                .map(document -> reviewEvidence(document, KLAVIYO_REVIEW_MARKERS))
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

    private Optional<String> providerKey(String html, List<Pattern> patterns) {
        if (html == null || html.isBlank()) {
            return Optional.empty();
        }
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(html);
            if (matcher.find() && hasText(matcher.group(1))) {
                return Optional.of(matcher.group(1).trim());
            }
        }
        return Optional.empty();
    }

    private Optional<MarkerEvidence> reviewEvidence(StorefrontDocument document, List<String> markers) {
        String html = document.html();
        if (html == null || html.isBlank()) {
            return Optional.empty();
        }
        String lowerHtml = html.toLowerCase(Locale.ROOT);
        for (String marker : markers) {
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
