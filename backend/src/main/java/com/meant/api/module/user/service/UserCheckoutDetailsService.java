package com.meant.api.module.user.service;

import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.repository.UserCheckoutDetailsRepository;
import com.meant.api.module.user.service.command.SaveUserCheckoutDetailsCommand;
import com.meant.api.module.user.service.dto.UserCheckoutDetailsResult;
import com.meant.api.module.user.service.query.GetUserCheckoutDetailsQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class UserCheckoutDetailsService {

    private final UserCheckoutDetailsRepository userCheckoutDetailsRepository;

    @Transactional(readOnly = true)
    public Optional<UserCheckoutDetailsResult> get(@NotNull @Valid GetUserCheckoutDetailsQuery query) {
        return userCheckoutDetailsRepository.findById(query.userId())
                .map(UserCheckoutDetailsResult::from);
    }

    @Transactional
    public UserCheckoutDetailsResult save(@NotNull @Valid SaveUserCheckoutDetailsCommand command) {
        Instant now = Instant.now();
        userCheckoutDetailsRepository.upsert(
                command.userId(),
                command.email().trim(),
                command.firstName().trim(),
                command.lastName().trim(),
                trimToNull(command.phoneNumber()),
                command.streetAddress().trim(),
                trimToNull(command.extendedAddress()),
                command.addressLocality().trim(),
                trimToNull(command.addressRegion()),
                command.postalCode().trim(),
                command.addressCountry().trim().toUpperCase(Locale.ROOT),
                now
        );
        return userCheckoutDetailsRepository.findById(command.userId())
                .map(UserCheckoutDetailsResult::from)
                .orElseThrow(() -> UserException.notFound(
                        "Saved checkout details not found for user: " + command.userId()));
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
