package com.meant.api.module.user.service.command;

import com.meant.api.module.user.service.dto.UserProductSearchConversationMessage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record QualifyUserProductSearchCommand(
        @NotNull
        UUID userId,

        @NotNull
        UUID conversationId,

        UUID qualificationId,

        @NotBlank
        @Size(max = 8000)
        String message,

        UUID merchantId,

        @NotNull
        List<@Valid UserProductSearchConversationMessage> conversation,

        UUID requestId,

        Instant expectedQualificationUpdatedAt,

        @Size(max = 500)
        String trustedReferenceProductText
) {

    public QualifyUserProductSearchCommand {
        conversation = conversation == null ? List.of() : List.copyOf(conversation);
        trustedReferenceProductText = trustedReferenceProductText == null
                        || trustedReferenceProductText.isBlank()
                ? null
                : trustedReferenceProductText.trim();
    }

    /**
     * Returns the stable qualification identity for one trusted request, independent of its payload.
     *
     * <p>Keeping the message out of this key lets the service detect accidental or malicious reuse
     * of one request ID with different text instead of silently creating a second qualification.</p>
     */
    public UUID requestQualificationId() {
        return requestQualificationId(userId, conversationId, merchantId, requestId);
    }

    /** Computes the same stable identity before remote qualification starts. */
    public static UUID requestQualificationId(
            UUID userId,
            UUID conversationId,
            UUID merchantId,
            UUID requestId
    ) {
        if (requestId == null) {
            return null;
        }
        String identity = "product-search-qualification:"
                + userId
                + ":"
                + conversationId
                + ":"
                + Objects.toString(merchantId, "broad")
                + ":"
                + requestId;
        return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
    }

    /** Backwards-compatible constructor for callers without trusted conversation context. */
    public QualifyUserProductSearchCommand(
            UUID userId,
            UUID conversationId,
            UUID qualificationId,
            String message,
            UUID merchantId,
            List<UserProductSearchConversationMessage> conversation,
            UUID requestId,
            Instant expectedQualificationUpdatedAt
    ) {
        this(
                userId,
                conversationId,
                qualificationId,
                message,
                merchantId,
                conversation,
                requestId,
                expectedQualificationUpdatedAt,
                null
        );
    }

    public QualifyUserProductSearchCommand(
            UUID userId,
            UUID conversationId,
            UUID qualificationId,
            String message,
            UUID merchantId,
            List<UserProductSearchConversationMessage> conversation
    ) {
        this(userId, conversationId, qualificationId, message, merchantId, conversation, null, null, null);
    }

    public QualifyUserProductSearchCommand(
            UUID userId,
            UUID conversationId,
            UUID qualificationId,
            String message,
            UUID merchantId,
            List<UserProductSearchConversationMessage> conversation,
            UUID requestId
    ) {
        this(userId, conversationId, qualificationId, message, merchantId, conversation, requestId, null, null);
    }

    /** Backwards-compatible constructor for callers without trusted conversation context. */
    public QualifyUserProductSearchCommand(
            UUID userId,
            UUID conversationId,
            UUID qualificationId,
            String message,
            UUID merchantId
    ) {
        this(userId, conversationId, qualificationId, message, merchantId, List.of(), null, null, null);
    }
}
