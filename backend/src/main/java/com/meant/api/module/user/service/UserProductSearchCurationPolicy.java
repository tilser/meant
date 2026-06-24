package com.meant.api.module.user.service;

import com.meant.api.module.user.service.dto.UserProductSearchProductResult;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class UserProductSearchCurationPolicy {

    private static final int MIN_VISIBLE_CURATOR_SCORE = 50;

    public List<UserProductSearchProductResult> visibleProducts(List<UserProductSearchProductResult> products) {
        if (products == null || products.isEmpty()) {
            return List.of();
        }
        return products.stream()
                .filter(this::isVisible)
                .toList();
    }

    private boolean isVisible(UserProductSearchProductResult product) {
        return product.matchScore() >= MIN_VISIBLE_CURATOR_SCORE;
    }
}
