package com.meant.api.module.user.service;

import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.user.constant.UserProductSearchQualificationStatus;
import com.meant.api.module.user.entity.UserDiscoverProductResultItem;
import com.meant.api.module.user.entity.UserDiscoverProductResultSet;
import com.meant.api.module.user.entity.UserProductSearchQualification;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.repository.UserDiscoverProductResultItemRepository;
import com.meant.api.module.user.repository.UserDiscoverProductResultSetRepository;
import com.meant.api.module.user.repository.UserProductSearchQualificationRepository;
import com.meant.api.module.user.service.command.ReplaceUserDiscoverProductResultSetCommand;
import com.meant.api.module.user.service.dto.UserDiscoverProductResultSetSnapshot;
import com.meant.api.module.user.service.query.GetUserDiscoverProductResultSetQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/** Stores and resolves only ordered canonical identifiers; provider facts never cross this boundary. */
@Service
@Validated
@RequiredArgsConstructor
public class UserDiscoverProductResultSetPersistenceService {

    private final UserDiscoverProductResultSetRepository resultSetRepository;
    private final UserDiscoverProductResultItemRepository resultItemRepository;
    private final UserProductSearchQualificationRepository qualificationRepository;
    private final UserCanonicalProductReferencePersistenceService productReferencePersistenceService;

    @Transactional
    public UUID replace(@NotNull @Valid ReplaceUserDiscoverProductResultSetCommand command) {
        UserProductSearchQualification qualification = qualificationRepository
                .findByIdForUpdate(command.qualificationId())
                .filter(value -> value.getUserId().equals(command.userId()))
                .filter(value -> value.getConversationId().equals(command.conversationId()))
                .filter(value -> value.getStatus() == UserProductSearchQualificationStatus.READY)
                .orElseThrow(() -> UserException.notFound("Product-search qualification not found"));
        List<CanonicalProduct> uniqueProducts = uniqueProducts(command.products());
        productReferencePersistenceService.replace(command.userId(), uniqueProducts);

        Instant now = Instant.now();
        UserDiscoverProductResultSet resultSet = resultSetRepository
                .findByQualificationIdAndPageOffsetAndResultLimit(
                        qualification.getId(), command.offset(), command.resultLimit())
                .map(existing -> refresh(
                        existing,
                        command,
                        uniqueProducts.size(),
                        now
                ))
                .orElseGet(() -> UserDiscoverProductResultSet.create(
                        command.userId(),
                        command.conversationId(),
                        command.qualificationId(),
                        command.offset(),
                        command.resultLimit(),
                        uniqueProducts.size(),
                        command.nextOffset(),
                        command.hasMore(),
                        command.upstreamTruncated(),
                        now
                ));
        UserDiscoverProductResultSet saved = resultSetRepository.save(resultSet);
        resultItemRepository.deleteByResultSetId(saved.getId());
        resultItemRepository.flush();
        List<UserDiscoverProductResultItem> items = java.util.stream.IntStream
                .range(0, uniqueProducts.size())
                .mapToObj(index -> UserDiscoverProductResultItem.create(
                        saved.getId(),
                        uniqueProducts.get(index).key(),
                        command.offset() + index
                ))
                .toList();
        if (!items.isEmpty()) {
            resultItemRepository.saveAll(items);
        }
        return saved.getId();
    }

    @Transactional(readOnly = true)
    public UserDiscoverProductResultSetSnapshot getOwned(
            @NotNull @Valid GetUserDiscoverProductResultSetQuery query
    ) {
        UserDiscoverProductResultSet resultSet = resultSetRepository
                .findByIdAndUserIdAndConversationId(
                        query.resultSetId(), query.userId(), query.conversationId())
                .orElseThrow(() -> UserException.notFound("Discover product result set not found"));
        List<String> productKeys = resultItemRepository.findByResultSetIdOrderByResultRankAsc(resultSet.getId())
                .stream()
                .map(UserDiscoverProductResultItem::getCanonicalProductKey)
                .toList();
        return new UserDiscoverProductResultSetSnapshot(
                resultSet.getId(), resultSet.getResultCount(), productKeys);
    }

    private UserDiscoverProductResultSet refresh(
            UserDiscoverProductResultSet existing,
            ReplaceUserDiscoverProductResultSetCommand command,
            int resultCount,
            Instant now
    ) {
        if (!existing.getUserId().equals(command.userId())
                || !existing.getConversationId().equals(command.conversationId())) {
            throw UserException.notFound("Discover product result set not found");
        }
        existing.refresh(
                resultCount,
                command.nextOffset(),
                command.hasMore(),
                command.upstreamTruncated(),
                now
        );
        return existing;
    }

    private List<CanonicalProduct> uniqueProducts(List<CanonicalProduct> products) {
        Map<String, CanonicalProduct> unique = new LinkedHashMap<>();
        products.stream()
                .filter(java.util.Objects::nonNull)
                .forEach(product -> unique.putIfAbsent(product.key(), product));
        return List.copyOf(unique.values());
    }
}
