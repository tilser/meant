package com.meant.api.module.agent.service.command;

import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.QualifyUserProductSearchCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Caller input for starting or resuming server-owned product-search qualification. */
public record QualifyAgentProductSearchCommand(
        @NotNull @Valid EnsureUserProfileCommand profile,
        @NotNull UUID conversationId,
        UUID merchantId,
        UUID qualificationId,
        UUID triggeringMessageId,
        @NotBlank @Size(max = 8000) String authoritativeUserText,
        Instant expectedQualificationUpdatedAt,
        @Size(max = 500) String trustedReferenceProductText
) {

    public QualifyAgentProductSearchCommand {
        trustedReferenceProductText = trustedReferenceProductText == null
                        || trustedReferenceProductText.isBlank()
                ? null
                : trustedReferenceProductText.trim();
    }

    public UUID requestQualificationId() {
        return QualifyUserProductSearchCommand.requestQualificationId(
                profile.id(),
                conversationId,
                merchantId,
                triggeringMessageId
        );
    }

    public QualifyAgentProductSearchCommand(
            EnsureUserProfileCommand profile,
            UUID conversationId,
            UUID merchantId,
            UUID qualificationId,
            UUID triggeringMessageId,
            String authoritativeUserText,
            Instant expectedQualificationUpdatedAt
    ) {
        this(
                profile,
                conversationId,
                merchantId,
                qualificationId,
                triggeringMessageId,
                authoritativeUserText,
                expectedQualificationUpdatedAt,
                null
        );
    }

    public QualifyAgentProductSearchCommand(
            EnsureUserProfileCommand profile,
            UUID conversationId,
            UUID merchantId,
            UUID qualificationId,
            UUID triggeringMessageId,
            String authoritativeUserText
    ) {
        this(
                profile,
                conversationId,
                merchantId,
                qualificationId,
                triggeringMessageId,
                authoritativeUserText,
                null,
                null
        );
    }
}
