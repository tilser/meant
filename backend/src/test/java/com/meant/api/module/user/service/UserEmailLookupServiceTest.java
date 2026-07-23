package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meant.api.module.user.entity.User;
import com.meant.api.module.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserEmailLookupServiceTest {

    @Test
    void findsLowercaseCandidateFirstWithOnePrioritizedQuery() {
        UserRepository repository = mock(UserRepository.class);
        User lowercase = user("mixed@example.com");
        User exact = user("Mixed@Example.com");
        when(repository.findEmailCandidates(
                List.of("mixed@example.com", "Mixed@Example.com"),
                "mixed@example.com"
        )).thenReturn(List.of(lowercase, exact));
        UserEmailLookupService service = new UserEmailLookupService(repository);

        assertThat(service.findUserId("  Mixed@Example.com  ")).contains(lowercase.getId());

        verify(repository).findEmailCandidates(
                List.of("mixed@example.com", "Mixed@Example.com"),
                "mixed@example.com"
        );
    }

    @Test
    void fallsBackToTheExactCaseCandidateInTheSameQuery() {
        UserRepository repository = mock(UserRepository.class);
        User exact = user("Mixed@Example.com");
        when(repository.findEmailCandidates(
                List.of("mixed@example.com", "Mixed@Example.com"),
                "mixed@example.com"
        )).thenReturn(List.of(exact));
        UserEmailLookupService service = new UserEmailLookupService(repository);

        assertThat(service.findUserId("Mixed@Example.com")).contains(exact.getId());
    }

    @Test
    void deduplicatesAnAlreadyLowercaseCandidate() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.findEmailCandidates(
                List.of("user@example.com"),
                "user@example.com"
        )).thenReturn(List.of());
        UserEmailLookupService service = new UserEmailLookupService(repository);

        assertThat(service.findUserId("user@example.com")).isEmpty();

        verify(repository).findEmailCandidates(List.of("user@example.com"), "user@example.com");
    }

    @Test
    void skipsTheRepositoryForMissingEmail() {
        UserRepository repository = mock(UserRepository.class);
        UserEmailLookupService service = new UserEmailLookupService(repository);

        assertThat(service.findUserId(null)).isEmpty();
        assertThat(service.findUserId("   ")).isEmpty();
        verifyNoInteractions(repository);
    }

    private User user(String email) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .build();
    }
}
