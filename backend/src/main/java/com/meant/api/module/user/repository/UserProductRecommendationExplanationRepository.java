package com.meant.api.module.user.repository;

import com.meant.api.module.user.entity.UserProductRecommendationExplanation;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProductRecommendationExplanationRepository
        extends JpaRepository<UserProductRecommendationExplanation, UUID> {

    List<UserProductRecommendationExplanation> findByUserIdAndNormalizedQueryAndProfileHashAndModelAndPromptVersionAndProductKeyIn(
            UUID userId,
            String normalizedQuery,
            String profileHash,
            String model,
            String promptVersion,
            Collection<String> productKeys
    );

    List<UserProductRecommendationExplanation> findByUserIdAndProfileHashAndModelAndPromptVersionAndProductKeyIn(
            UUID userId,
            String profileHash,
            String model,
            String promptVersion,
            Collection<String> productKeys
    );
}
