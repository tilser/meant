package com.meant.api.module.user.service;

import com.meant.api.module.user.entity.User;
import com.meant.api.module.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserEmailLookupService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Optional<UUID> findUserId(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        String normalizedEmail = email.trim();
        return userRepository.findByEmail(normalizedEmail.toLowerCase())
                .or(() -> userRepository.findByEmail(normalizedEmail))
                .map(User::getId);
    }
}
