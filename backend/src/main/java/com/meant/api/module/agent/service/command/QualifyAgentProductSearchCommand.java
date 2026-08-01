package com.meant.api.module.agent.service.command;

import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.QualifyUserProductSearchCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Caller input for conversation-scoped search advice. */
public record QualifyAgentProductSearchCommand(
        @NotNull @Valid EnsureUserProfileCommand profile,
        @NotNull UUID conversationId,
        UUID merchantId,
        UUID contextMessageId,
        UUID requestId,
        @NotBlank @Size(max = 8000) String authoritativeUserText,
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
                requestId
        );
    }
}
