package com.meant.api.module.user.entity;

import com.meant.api.common.entity.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Durable idempotency binding between one trusted buyer message and its qualification.
 *
 * <p>A qualification can span several buyer messages. Keeping every request binding prevents an
 * answer such as {@code 46} from becoming a new search if catalog execution is retried after the
 * qualification has already advanced to {@code READY}.</p>
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "user_product_search_qualification_requests")
public class UserProductSearchQualificationRequest extends AssignedIdEntity<UUID> {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID qualificationId;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private UUID conversationId;

    @Column(updatable = false)
    private UUID merchantId;

    @Column(nullable = false, updatable = false)
    private String message;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public static UserProductSearchQualificationRequest create(
            UUID requestId,
            UUID qualificationId,
            UUID userId,
            UUID conversationId,
            UUID merchantId,
            String message,
            Instant now
    ) {
        return UserProductSearchQualificationRequest.builder()
                .id(requestId)
                .qualificationId(qualificationId)
                .userId(userId)
                .conversationId(conversationId)
                .merchantId(merchantId)
                .message(message)
                .createdAt(now)
                .build();
    }
}
