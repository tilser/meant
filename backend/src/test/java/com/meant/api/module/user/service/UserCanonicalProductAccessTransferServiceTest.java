package com.meant.api.module.user.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meant.api.module.user.service.command.TransferUserCanonicalProductAccessCommand;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserCanonicalProductAccessTransferServiceTest {

    private final UserCanonicalProductReferencePersistenceService referencePersistenceService =
            mock(UserCanonicalProductReferencePersistenceService.class);
    private final UserCanonicalProductSessionStore sessionStore = mock(UserCanonicalProductSessionStore.class);
    private final UserCanonicalProductAccessTransferService service =
            new UserCanonicalProductAccessTransferService(referencePersistenceService, sessionStore);

    @Test
    void copiesDeduplicatedConversationProductAccessToTheClaimingUser() {
        UUID guestUserId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        when(referencePersistenceService.copyReferences(
                guestUserId, targetUserId, List.of("product-a", "product-b"))).thenReturn(3);
        when(sessionStore.copyAccess(
                guestUserId, targetUserId, List.of("product-a", "product-b"))).thenReturn(2);

        service.transfer(new TransferUserCanonicalProductAccessCommand(
                guestUserId,
                targetUserId,
                List.of(" product-a ", "product-b", "product-a")
        ));

        verify(referencePersistenceService).copyReferences(
                guestUserId, targetUserId, List.of("product-a", "product-b"));
        verify(sessionStore).copyAccess(
                guestUserId, targetUserId, List.of("product-a", "product-b"));
    }

    @Test
    void skipsTransferWhenTheAnonymousAccountWasUpgradedInPlace() {
        UUID userId = UUID.randomUUID();

        service.transfer(new TransferUserCanonicalProductAccessCommand(
                userId, userId, List.of("product-a")));

        verifyNoInteractions(referencePersistenceService, sessionStore);
    }
}
