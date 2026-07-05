package com.meant.api.module.review.service;

import com.meant.api.module.review.constant.ReviewProductIdType;
import com.meant.api.module.review.constant.ReviewProviderType;
import com.meant.api.module.review.service.dto.ReviewProviderDetectionResult;
import com.meant.api.module.review.service.dto.StorefrontDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ReviewProviderDetectionService {

    private static final int EVIDENCE_RADIUS = 60;
    private static final ReviewProductIdType SHOPIFY_PRODUCT_ID = ReviewProductIdType.SHOPIFY_NUMERIC_ID;
    private static final List<ProviderDetector> PROVIDER_DETECTORS = List.of(
            detector(
                    ReviewProviderType.YOTPO,
                    false,
                    List.of(
                            "staticw2\\.yotpo\\.com(?:/|\\\\/)([A-Za-z0-9_-]+)(?:/|\\\\/)widget\\.js",
                            "cdn-widgetsrepository\\.yotpo\\.com(?:/|\\\\/)v1(?:/|\\\\/)loader(?:/|\\\\/)([A-Za-z0-9_-]+)",
                            "\\\\?\"yotpoStoreId\\\\?\"\\s*:\\s*\\\\?\"([A-Za-z0-9_-]+)\\\\?\""
                    ),
                    List.of(
                            "staticw2\\.yotpo\\.com",
                            "data-yotpo-product-id",
                            "data-yotpo-instance-id",
                            "data-yotpo-star-ratings-widget",
                            "yotpo-widget-instance",
                            "yotpo-reviews-section-data",
                            "yotpo-main-widget"
                    )
            ),
            detector(
                    ReviewProviderType.KLAVIYO,
                    true,
                    List.of(
                            "company_id=([A-Za-z0-9_-]+)",
                            "static\\.klaviyo\\.com(?:/|\\\\/)onsite(?:/|\\\\/)js(?:/|\\\\/)([A-Za-z0-9_-]+)(?:/|\\\\/)klaviyo\\.js",
                            "klaviyo\\.init\\s*\\(\\s*\\{[^}]*account\\s*:\\s*[\"']([A-Za-z0-9_-]+)[\"']",
                            "\"accountID\"\\s*:\\s*\"([A-Za-z0-9_-]+)\""
                    ),
                    List.of(
                            "klaviyo_reviews",
                            "kl_reviews",
                            "client_reviews",
                            "reviews(?:/|\\\\/)api(?:/|\\\\/)client_reviews"
                    )
            ),
            detector(
                    ReviewProviderType.OKENDO,
                    false,
                    List.of(
                            "\"subscriberId\"\\s*:\\s*\"([0-9a-fA-F-]+)\"",
                            "\\\\?\"subscriberId\\\\?\"\\s*:\\s*\\\\?\"([0-9a-fA-F-]+)\\\\?\"",
                            "okendoSubscriberId\\\\?\"\\s*,\\s*\\\\?\"([0-9a-fA-F-]+)",
                            "data-oke-reviews-subscriber-id=[\"']([^\"']+)[\"']"
                    ),
                    List.of(
                            "shopify://apps/okendo",
                            "cdn-static\\.okendo\\.io",
                            "static\\.okendo\\.io",
                            "widget\\.okendo\\.io",
                            "okendo-reviews\\.css",
                            "okendo-reviews-styles",
                            "oke-reviews-settings",
                            "data-oke-reviews",
                            "okeReviews",
                            "oke-reviews"
                    )
            ),
            detector(
                    ReviewProviderType.JUDGE_ME,
                    false,
                    List.of(
                            "data-shop-domain=[\"']([^\"']+)[\"']",
                            "\"shop_domain\"\\s*:\\s*\"([^\"]+)\""
                    ),
                    List.of(
                            "shopify://apps/judge-me-reviews",
                            "cdnwidget\\.judge\\.me",
                            "cdn\\.judge\\.me",
                            "judgeme-\\d+(?:/|\\\\/)assets",
                            "judgeme_core",
                            "jdgm-settings-script",
                            "jdgm-widget",
                            "jdgm-preview-badge",
                            "jdgm-rev-widg",
                            "jdgm-all-reviews-widget"
                    )
            ),
            detector(
                    ReviewProviderType.BAZAARVOICE,
                    false,
                    List.of(
                            "apps\\.bazaarvoice\\.com(?:/|\\\\/)deployments(?:/|\\\\/)([A-Za-z0-9_-]+)"
                    ),
                    List.of(
                            "apps\\.bazaarvoice\\.com(?:/|\\\\/)deployments",
                            "display\\.ugc\\.bazaarvoice\\.com",
                            "data-bv-show",
                            "BVRRContainer",
                            "bv_main_container",
                            "bvloader",
                            "bazaarvoiceEnabled\\\\?\"?\\s*:\\s*true",
                            "bazaarvoiceReviewHighlightsEnabled\\\\?\"?\\s*:\\s*true"
                    )
            ),
            detector(
                    ReviewProviderType.STAMPED,
                    false,
                    List.of(
                            "stamped\\.io[^\"']*shop=([^&\"'\\\\]+)",
                            "stamped[^\"']*shop=([^&\"'\\\\]+\\.myshopify\\.com)"
                    ),
                    List.of(
                            "cdn1?\\.stamped\\.io(?:/|\\\\/)(?:files|static|widgets)",
                            "cdn-stamped-io\\.azureedge\\.net(?:/|\\\\/)files(?:/|\\\\/)widget",
                            "stamped-main-widget",
                            "stamped-product-reviews",
                            "stamped-badge"
                    )
            ),
            detector(
                    ReviewProviderType.REVIEWS_IO,
                    false,
                    List.of(
                            "data-store-name=[\"']([^\"']+)[\"']",
                            "data-store=[\"']([^\"']+)[\"']",
                            "\\bstore\\s*:\\s*[\"']([^\"']+)[\"']"
                    ),
                    List.of(
                            "widget\\.reviews\\.io",
                            "widget\\.reviews\\.co\\.uk",
                            "reviews\\.co\\.uk(?:/|\\\\/)rating-snippet",
                            "reviews\\.io(?:/|\\\\/)rating-snippet",
                            "RUKRatingSnippet",
                            "ruk_rating_snippet"
                    )
            ),
            detector(
                    ReviewProviderType.LOOX,
                    false,
                    List.of(
                            "loox\\.io(?:/|\\\\/)widget(?:/|\\\\/)([A-Za-z0-9_-]+)"
                    ),
                    List.of(
                            "loox\\.io(?:/|\\\\/)widget",
                            "loox-rating",
                            "looxReviews",
                            "looxReviewsFrame",
                            "loox-widget",
                            "loox-rating-content",
                            "#looxReviews"
                    )
            ),
            detector(
                    ReviewProviderType.JUNIP,
                    false,
                    List.of(
                            "data-store-key=[\"']([^\"']+)[\"']"
                    ),
                    List.of(
                            "shopify://apps/junip",
                            "junip-store-key",
                            "junip-reviews",
                            "junip-product-review",
                            "junip\\.co"
                    )
            ),
            detector(
                    ReviewProviderType.POWER_REVIEWS,
                    false,
                    List.of(),
                    List.of(
                            "ui\\.powerreviews\\.com",
                            "components\\.powerreviews\\.com",
                            "POWERREVIEWS",
                            "data-pr-page-id",
                            "pr_page_id",
                            "product-power-reviews",
                            "pr-review",
                            "pr-snippet"
                    )
            ),
            detector(
                    ReviewProviderType.OPINEW,
                    false,
                    List.of(
                            "cdn\\.opinew\\.com[^\"']*shop=([^&\"'\\\\]+)"
                    ),
                    List.of(
                            "cdn\\.opinew\\.com",
                            "opinew-reviews-product-page\\.js",
                            "opinew-active\\.js",
                            "opw-widget",
                            "opinew-stars-plugin"
                    )
            ),
            detector(
                    ReviewProviderType.AIR_REVIEWS,
                    false,
                    List.of(),
                    List.of(
                            "air-reviews(?:-|\\.)",
                            "AirReviews-Widget",
                            "airreviews"
                    )
            ),
            detector(
                    ReviewProviderType.SHOPIFY_PRODUCT_REVIEWS,
                    false,
                    List.of(),
                    List.of(
                            "shopify-product-reviews-badge",
                            "id=[\"']shopify-product-reviews[\"']",
                            "<[^>]+class=[\"'][^\"']*spr-badge",
                            "<[^>]+class=[\"'][^\"']*spr-summary"
                    )
            ),
            detector(
                    ReviewProviderType.TRUSTPILOT,
                    false,
                    List.of(
                            "data-businessunit-id=[\"']([^\"']+)[\"']"
                    ),
                    List.of(
                            "widget\\.trustpilot\\.com",
                            "ecommplugins-scripts\\.trustpilot\\.com",
                            "trustpilot-widget"
                    )
            )
    );

    public ReviewProviderDetectionResult detect(String sourceUrl, String html) {
        return detect(List.of(new StorefrontDocument(sourceUrl, html)));
    }

    public ReviewProviderDetectionResult detect(List<StorefrontDocument> documents) {
        for (ProviderDetector detector : PROVIDER_DETECTORS) {
            Optional<MarkerEvidence> evidence = documents.stream()
                    .map(document -> reviewEvidence(document, detector.reviewEvidencePatterns()))
                    .flatMap(Optional::stream)
                    .findFirst();
            if (evidence.isEmpty()) {
                continue;
            }
            Optional<String> providerKey = documents.stream()
                    .map(StorefrontDocument::html)
                    .map(html -> providerKey(html, detector.providerKeyPatterns()))
                    .flatMap(Optional::stream)
                    .findFirst();
            if (detector.providerKeyRequired() && providerKey.isEmpty()) {
                continue;
            }
            MarkerEvidence markerEvidence = evidence.get();
            return ReviewProviderDetectionResult.detected(
                    detector.provider(),
                    providerKey.orElse(null),
                    detector.productIdType(),
                    markerEvidence.sourceUrl(),
                    markerEvidence.evidence()
            );
        }
        return ReviewProviderDetectionResult.notFound();
    }

    private static ProviderDetector detector(
            ReviewProviderType provider,
            boolean providerKeyRequired,
            List<String> providerKeyPatterns,
            List<String> reviewEvidencePatterns
    ) {
        return new ProviderDetector(
                provider,
                SHOPIFY_PRODUCT_ID,
                providerKeyRequired,
                compile(providerKeyPatterns),
                compile(reviewEvidencePatterns)
        );
    }

    private static List<Pattern> compile(List<String> regexes) {
        List<Pattern> patterns = new ArrayList<>(regexes.size());
        for (String regex : regexes) {
            patterns.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL));
        }
        return patterns;
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

    private Optional<MarkerEvidence> reviewEvidence(StorefrontDocument document, List<Pattern> patterns) {
        String html = document.html();
        if (html == null || html.isBlank()) {
            return Optional.empty();
        }
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(html);
            if (matcher.find()) {
                return Optional.of(new MarkerEvidence(document.sourceUrl(), snippet(html, matcher.start())));
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

    private record ProviderDetector(
            ReviewProviderType provider,
            ReviewProductIdType productIdType,
            boolean providerKeyRequired,
            List<Pattern> providerKeyPatterns,
            List<Pattern> reviewEvidencePatterns
    ) {
    }
}
