package com.meant.api.plugin.catalog.common.service;

import com.meant.api.plugin.catalog.common.dto.OfferMerchantScope;
import com.meant.api.plugin.catalog.common.dto.ProductRankingExplanation;
import com.meant.api.plugin.catalog.common.dto.ProductRetrievalSignal;
import com.meant.api.plugin.catalog.common.service.ProductRankingFeatureExtractor.ScoredProduct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Feasibility-aware bounded diversity over explicit strongest acquisition evidence. */
@Component
@RequiredArgsConstructor
public class ProductDiversityPolicy {

    public static final String VERSION = "source-merchant-window-v2";
    private final DiversityFeasibility feasibility;

    Result apply(List<ScoredProduct> sorted, int requestedWindow) {
        int window = Math.min(requestedWindow, sorted.size());
        if (window < 2) return new Result(sorted, ProductRankingExplanation.DiversityPolicyOutcome.STRICT);
        List<Entry> entries = sorted.stream().map(product -> new Entry(product, attribution(product))).toList();
        int baseSourceCap = Math.max(1, (int) Math.ceil(window * 0.60d));
        int baseMerchantCap = Math.max(1, (int) Math.ceil(window * 0.50d));
        Caps caps = feasibleCaps(entries, window, baseSourceCap, baseMerchantCap);
        List<Entry> selected = select(entries, window, caps);
        Set<String> keys = selected.stream().map(entry -> entry.product().product().key()).collect(Collectors.toSet());
        List<ScoredProduct> result = new ArrayList<>(selected.stream().map(Entry::product).toList());
        entries.stream().filter(entry -> !keys.contains(entry.product().product().key())).map(Entry::product).forEach(result::add);
        ProductRankingExplanation.DiversityPolicyOutcome outcome = caps.source() == baseSourceCap && caps.merchant() == baseMerchantCap
                ? ProductRankingExplanation.DiversityPolicyOutcome.STRICT
                : ProductRankingExplanation.DiversityPolicyOutcome.RELAXED_INFEASIBLE;
        return new Result(List.copyOf(result), outcome);
    }

    private Caps feasibleCaps(List<Entry> entries, int window, int sourceBase, int merchantBase) {
        List<Attribution> all = entries.stream().map(Entry::attribution).toList();
        for (int total = 0; total <= window * 2; total++) {
            for (int sourceExtra = 0; sourceExtra <= total; sourceExtra++) {
                int merchantExtra = total - sourceExtra;
                int sourceCap = Math.min(window, sourceBase + sourceExtra);
                int merchantCap = Math.min(window, merchantBase + merchantExtra);
                if (feasibility.maximum(all, sourceCap, merchantCap, Map.of(), Map.of()) >= window) return new Caps(sourceCap, merchantCap);
            }
        }
        return new Caps(window, window);
    }

    private List<Entry> select(List<Entry> entries, int window, Caps caps) {
        List<Entry> selected = new ArrayList<>();
        Map<String, Integer> sourceUsed = new HashMap<>();
        Map<String, Integer> merchantUsed = new HashMap<>();
        for (int index = 0; index < entries.size() && selected.size() < window; index++) {
            Entry candidate = entries.get(index);
            if (sourceUsed.getOrDefault(candidate.attribution().source(), 0) >= caps.source()
                    || merchantUsed.getOrDefault(candidate.attribution().merchant(), 0) >= caps.merchant()) continue;
            Map<String, Integer> nextSource = incremented(sourceUsed, candidate.attribution().source());
            Map<String, Integer> nextMerchant = incremented(merchantUsed, candidate.attribution().merchant());
            int needed = window - selected.size() - 1;
            List<Attribution> remaining = entries.subList(index + 1, entries.size()).stream().map(Entry::attribution).toList();
            if (feasibility.maximum(remaining, caps.source(), caps.merchant(), nextSource, nextMerchant) < needed) continue;
            selected.add(candidate);
            sourceUsed = nextSource;
            merchantUsed = nextMerchant;
        }
        return selected;
    }

    private Map<String, Integer> incremented(Map<String, Integer> values, String key) {
        Map<String, Integer> copy = new HashMap<>(values);
        copy.merge(key, 1, Integer::sum);
        return copy;
    }

    private Attribution attribution(ScoredProduct product) {
        int strongest = product.product().retrievalSignals().stream().mapToInt(ProductRetrievalSignal::valueBasisPoints).max().orElse(-1);
        List<ProductRetrievalSignal> signals = product.product().retrievalSignals().stream()
                .filter(signal -> signal.valueBasisPoints() == strongest).toList();
        Set<String> sources = signals.stream().map(signal -> signal.source().provider().value() + ":" + signal.source().type()).collect(Collectors.toSet());
        Set<String> merchants = signals.stream().map(ProductRetrievalSignal::merchantScope).filter(java.util.Objects::nonNull)
                .map(ProductDiversityPolicy::merchantKey).collect(Collectors.toSet());
        return new Attribution(group(sources, "UNKNOWN_SOURCE", "MULTI_SOURCE"), group(merchants, "UNKNOWN_MERCHANT", "MULTI_MERCHANT"));
    }

    private static String group(Set<String> values, String unknown, String multiple) {
        return values.isEmpty() ? unknown : values.size() == 1 ? values.iterator().next() : multiple;
    }

    private static String merchantKey(OfferMerchantScope scope) {
        return scope.externalMerchantIdentity() == null ? "local:" + scope.merchantIntegrationFallbackId()
                : "external:" + scope.externalMerchantIdentity().namespace() + ":" + scope.externalMerchantIdentity().value();
    }

    record Attribution(String source, String merchant) { }
    private record Entry(ScoredProduct product, Attribution attribution) { }
    private record Caps(int source, int merchant) { }
    record Result(List<ScoredProduct> products, ProductRankingExplanation.DiversityPolicyOutcome outcome) { }
}
