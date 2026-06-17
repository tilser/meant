package com.meant.api.module.user.repository;

import com.meant.api.module.user.entity.UserProductSearchQueryIntent;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProductSearchQueryIntentRepository extends JpaRepository<UserProductSearchQueryIntent, UUID> {

    Optional<UserProductSearchQueryIntent> findByNormalizedOriginalQueryAndModelAndPromptVersion(
            String normalizedOriginalQuery,
            String model,
            String promptVersion
    );
}
