package com.meant.api.module.user.service;

import com.meant.api.module.user.constant.UserProductSearchPagination;
import com.meant.api.module.user.service.command.CurateUserProductSearchCommand;
import com.meant.api.module.user.service.dto.UserInventoryRecommendationSignal;
import com.meant.api.module.user.service.dto.UserProductRecommendationExplanationResult;
import com.meant.api.module.user.service.dto.UserProductSearchCuratorResult;
import com.meant.api.module.user.service.dto.UserProductSearchProductResult;
import com.meant.api.module.user.service.dto.UserProductSearchProductSnapshot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class UserProductSearchCuratorService {

    private final UserInventoryService userInventoryService;
    private final UserProductRecommendationExplanationService userProductRecommendationExplanationService;
    private final UserProductPreferenceMatchCuratorService userProductPreferenceMatchCuratorService;
    private final UserTasteRankingService userTasteRankingService;
    private final UserProductSearchCurationPolicy userProductSearchCurationPolicy;
    private final UserProductSearchProductResultMapper userProductSearchProductResultMapper;

    public UserProductSearchCuratorResult curate(
            @NotNull @Valid CurateUserProductSearchCommand command
    ) {
        Map<String, UserInventoryRecommendationSignal> inventorySignals =
                userInventoryService.recommendationSignals(command.userId(), command.products());
        Map<String, UserProductRecommendationExplanationResult> explanations =
                userProductRecommendationExplanationService.explain(
                        command.userId(),
                        command.query(),
                        command.normalizedQuery(),
                        command.profileHash(),
                        command.settings(),
                        command.products(),
                        inventorySignals
                );
        Map<String, UserProductRecommendationExplanationResult> curatedExplanations =
                userProductPreferenceMatchCuratorService.curate(
                        command.products(),
                        command.settings(),
                        explanations,
                        inventorySignals
                );
        List<UserProductSearchProductResult> productResults = command.products().stream()
                .map(product -> productResult(product, curatedExplanations, inventorySignals, command))
                .toList();
        List<UserProductSearchProductResult> rankedProducts = userTasteRankingService.rank(
                productResults,
                command.tasteProfile(),
                command.settings()
        );
        List<UserProductSearchProductResult> visibleProducts =
                userProductSearchCurationPolicy.visibleProducts(rankedProducts, command.settings());
        return new UserProductSearchCuratorResult(
                visibleProducts,
                page(visibleProducts, command.offset(), command.limit()),
                curatedExplanations
        );
    }

    private UserProductSearchProductResult productResult(
            UserProductSearchProductSnapshot product,
            Map<String, UserProductRecommendationExplanationResult> explanations,
            Map<String, UserInventoryRecommendationSignal> inventorySignals,
            CurateUserProductSearchCommand command
    ) {
        return userProductSearchProductResultMapper.from(
                product,
                explanationFor(product, explanations, inventorySignals),
                command.now()
        );
    }

    private UserProductRecommendationExplanationResult explanationFor(
            UserProductSearchProductSnapshot product,
            Map<String, UserProductRecommendationExplanationResult> explanations,
            Map<String, UserInventoryRecommendationSignal> inventorySignals
    ) {
        UserProductRecommendationExplanationResult explanation = explanations.get(product.productKey());
        return explanation == null
                ? UserProductRecommendationExplanationResult.fallback(
                        product.productKey(),
                        product.productHash(),
                        inventorySignals == null ? null : inventorySignals.get(product.productKey())
                )
                : explanation;
    }

    private List<UserProductSearchProductResult> page(
            List<UserProductSearchProductResult> products,
            int offset,
            int limit
    ) {
        if (offset < 0 || offset >= products.size() || limit <= 0) {
            return List.of();
        }
        int toIndex = Math.min(pageEnd(offset, limit), products.size());
        return offset >= toIndex ? List.of() : products.subList(offset, toIndex);
    }

    private int pageEnd(int offset, int limit) {
        return Math.min(offset + limit, UserProductSearchPagination.MAX_RESULT_WINDOW);
    }
}
