package com.meant.api.module.user.service;

import com.meant.api.common.properties.OpenRouterProperties;
import com.meant.api.module.user.entity.UserProductSearchEvent;
import com.meant.api.module.user.properties.UserProductSearchProperties;
import com.meant.api.module.user.repository.UserProductSearchEventRepository;
import com.meant.api.module.user.service.dto.UserPopularProductSearchResult;
import com.meant.api.module.user.service.dto.UserProductSearchQueryIntentResult;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserProductSearchEventService {

    private static final Pattern PRIVATE_QUERY_PATTERN = Pattern.compile(
            "(@|\\b\\d{3}[-. ]?\\d{2}[-. ]?\\d{4}\\b|\\b\\d{10,}\\b|\\b(password|ssn|social security)\\b)",
            Pattern.CASE_INSENSITIVE
    );

    private final UserProductSearchEventRepository userProductSearchEventRepository;
    private final UserProductSearchProperties userProductSearchProperties;
    private final OpenRouterProperties openRouterProperties;

    @Transactional
    public void record(
            UUID userId,
            UUID merchantId,
            UserProductSearchQueryIntentResult queryIntent,
            int resultCount,
            Instant now
    ) {
        userProductSearchEventRepository.save(UserProductSearchEvent.from(
                userId,
                merchantId,
                openRouterProperties.models().productSearchQueryParser(),
                userProductSearchProperties.queryParserPromptVersion(),
                queryIntent,
                resultCount,
                now
        ));
    }

    @Transactional(readOnly = true)
    public List<UserPopularProductSearchResult> popular(Instant now) {
        LinkedHashMap<String, UserPopularProductSearchResult> results = new LinkedHashMap<>();
        appendPopular(results, now.minus(userProductSearchProperties.popularSearchWindow()));
        if (results.size() < userProductSearchProperties.popularSearchLimit()) {
            appendPopular(results, now.minus(userProductSearchProperties.popularSearchFallbackWindow()));
        }
        return results.values().stream()
                .limit(userProductSearchProperties.popularSearchLimit())
                .toList();
    }

    private void appendPopular(
            LinkedHashMap<String, UserPopularProductSearchResult> results,
            Instant since
    ) {
        userProductSearchEventRepository.findPopularSearches(
                        since,
                        userProductSearchProperties.popularSearchMinDistinctUsers(),
                        userProductSearchProperties.popularSearchMaxDisplayLength(),
                        PageRequest.of(0, userProductSearchProperties.popularSearchLimit() * 2)
                )
                .stream()
                .filter(this::safeForDisplay)
                .forEach(result -> results.putIfAbsent(
                        result.displayQuery().toLowerCase(Locale.ROOT),
                        result
                ));
    }

    private boolean safeForDisplay(UserPopularProductSearchResult result) {
        String displayQuery = result.displayQuery();
        return displayQuery != null
                && !displayQuery.isBlank()
                && displayQuery.length() <= userProductSearchProperties.popularSearchMaxDisplayLength()
                && !PRIVATE_QUERY_PATTERN.matcher(displayQuery).find();
    }
}
