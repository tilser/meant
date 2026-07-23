package com.meant.api.module.user.service;

import static com.meant.api.common.util.CollectionUtils.safeList;

import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
import com.meant.api.module.user.constant.UserInventoryCategory;
import com.meant.api.module.user.constant.UserInventoryRecommendationRelationship;
import com.meant.api.module.user.constant.UserInventorySource;
import com.meant.api.module.user.entity.UserInventoryItem;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.properties.UserCollectionProperties;
import com.meant.api.module.user.repository.UserInventoryItemRepository;
import com.meant.api.module.user.service.command.CreateUserInventoryItemCommand;
import com.meant.api.module.user.service.command.DeleteUserInventoryItemCommand;
import com.meant.api.module.user.service.command.ImportPurchasedInventoryItemsCommand;
import com.meant.api.module.user.service.command.UpdateUserInventoryItemCommand;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.dto.UserInventoryExportResult;
import com.meant.api.module.user.service.dto.UserInventoryCommerceReference;
import com.meant.api.module.user.service.dto.UserInventoryItemResult;
import com.meant.api.module.user.service.dto.UserInventoryProfileSummary;
import com.meant.api.module.user.service.dto.UserInventoryRecommendationSignal;
import com.meant.api.module.user.service.dto.UserInventorySelectedOption;
import com.meant.api.module.user.service.dto.UserProductSearchProductSnapshot;
import com.meant.api.module.user.service.query.ExportUserInventoryQuery;
import com.meant.api.module.user.service.query.ListUserInventoryItemsQuery;
import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@Validated
@RequiredArgsConstructor
public class UserInventoryService {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<UserInventorySelectedOption>> SELECTED_OPTION_LIST_TYPE =
            new TypeReference<>() {
            };
    private static final Pattern SPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern NON_ALPHANUMERIC_PATTERN = Pattern.compile("[^a-z0-9 ]");
    private static final Pattern APPAREL_PATTERN = Pattern.compile(
            "\\b(tee|t-shirt|shirt|sweater|jacket|coat|dress|jeans|pants|trouser|shoe|sneaker|sock|cotton|wool|linen|merino)\\b"
    );
    private static final Pattern PANTRY_PATTERN = Pattern.compile(
            "\\b(cereal|oat|almond|oil|snack|coffee|tea|grocery|pantry|rice|pasta|sauce|milk|breakfast|flour|sugar)\\b"
    );
    private static final Pattern HOME_PATTERN = Pattern.compile(
            "\\b(home|kitchen|brewer|lamp|chair|table|mug|cleaner|vacuum|sheet|towel|appliance|decor|storage)\\b"
    );

    private final UserService userService;
    private final UserInventoryItemRepository userInventoryItemRepository;
    private final UserCollectionProperties userCollectionProperties;
    private final ObjectMapper objectMapper;

    @Transactional
    public List<UserInventoryItemResult> list(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid ListUserInventoryItemsQuery query
    ) {
        validateUser(profileCommand, query.userId(), "Inventory user does not match authenticated user");
        userService.ensureProfile(profileCommand);
        return inventoryItems(query).stream()
                .map(this::toResult)
                .toList();
    }

    @Transactional
    public UserInventoryExportResult export(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid ExportUserInventoryQuery query
    ) {
        validateUser(profileCommand, query.userId(), "Inventory export user does not match authenticated user");
        userService.ensureProfile(profileCommand);
        return new UserInventoryExportResult(
                Instant.now(),
                userInventoryItemRepository.findByUserIdOrderByUpdatedAtDesc(query.userId()).stream()
                        .map(this::toResult)
                        .toList()
        );
    }

    @Transactional
    public UserInventoryItemResult create(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid CreateUserInventoryItemCommand command
    ) {
        validateUser(profileCommand, command.userId(), "Inventory item user does not match authenticated user");
        userService.ensureProfile(profileCommand);
        validateInventoryQuota(command.userId());
        String photoPath = UserOwnedImagePathValidator.normalize(
                command.userId(), command.photoPath(), "Inventory photo path");
        Instant now = Instant.now();
        UserInventoryItem item = UserInventoryItem.create(command.userId(), snapshot(command, photoPath), now);
        return toResult(userInventoryItemRepository.save(item));
    }

    @Transactional
    public UserInventoryItemResult update(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid UpdateUserInventoryItemCommand command
    ) {
        validateUser(profileCommand, command.userId(), "Inventory item user does not match authenticated user");
        userService.ensureProfile(profileCommand);
        UserInventoryItem item = userInventoryItemRepository
                .findByIdAndUserId(command.itemId(), command.userId())
                .orElseThrow(() -> UserException.notFound("Inventory item not found: " + command.itemId()));
        String replacementPhotoPath = command.photoPath() == null
                ? null
                : UserOwnedImagePathValidator.normalize(
                        command.userId(), command.photoPath(), "Inventory photo path");
        Instant now = Instant.now();
        item.replaceSnapshot(snapshot(item, command, replacementPhotoPath), now);
        return toResult(userInventoryItemRepository.save(item));
    }

    @Transactional
    public void delete(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid DeleteUserInventoryItemCommand command
    ) {
        validateUser(profileCommand, command.userId(), "Inventory item user does not match authenticated user");
        userService.ensureProfile(profileCommand);
        long deleted = userInventoryItemRepository.deleteByIdAndUserId(command.itemId(), command.userId());
        if (deleted == 0) {
            throw UserException.notFound("Inventory item not found: " + command.itemId());
        }
    }

    @Transactional
    public void importPurchasedItems(@NotNull @Valid ImportPurchasedInventoryItemsCommand command) {
        if (command.items().isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        long itemCount = userInventoryItemRepository.countByUserId(command.userId());
        int quota = userCollectionProperties.inventory().quota();
        Set<String> productKeys = command.items().stream()
                .map(ImportPurchasedInventoryItemsCommand.PurchasedItem::productKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, UserInventoryItem> existingByProductKey = userInventoryItemRepository
                .findByUserIdAndSourceAndSourceProductKeyIn(
                        command.userId(),
                        UserInventorySource.MEANT_PURCHASE,
                        productKeys
                )
                .stream()
                .collect(Collectors.toMap(
                        UserInventoryItem::getSourceProductKey,
                        item -> item,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<String, UserInventoryItem> changedItems = new LinkedHashMap<>();
        for (ImportPurchasedInventoryItemsCommand.PurchasedItem purchasedItem : command.items()) {
            UserInventoryItem existing = existingByProductKey.get(purchasedItem.productKey());
            if (existing != null
                    && Objects.equals(existing.getSourceCheckoutAttemptId(), command.checkoutAttemptId())) {
                continue;
            }
            UserInventoryItem.Snapshot snapshot = purchasedSnapshot(purchasedItem, existing, command);
            UserInventoryItem item;
            if (existing == null) {
                if (itemCount >= quota) {
                    throw new UserException("Inventory item quota exceeded for user " + command.userId());
                }
                item = UserInventoryItem.create(command.userId(), snapshot, now);
                itemCount++;
            } else {
                item = existing.replaceSnapshot(snapshot, now);
            }
            existingByProductKey.put(purchasedItem.productKey(), item);
            changedItems.put(purchasedItem.productKey(), item);
        }
        if (!changedItems.isEmpty()) {
            userInventoryItemRepository.saveAll(changedItems.values());
        }
    }

    @Transactional(readOnly = true)
    public String inventoryProfileHash(UUID userId) {
        UserInventoryProfileSummary summary = userInventoryItemRepository.summarizeProfileByUserId(userId);
        if (summary.itemCount() == 0) {
            return "inventory:none";
        }
        return "inventory:" + summary.itemCount() + ":" + value(summary.lastUpdatedAt());
    }

    @Transactional(readOnly = true)
    public Map<String, UserInventoryRecommendationSignal> recommendationSignals(
            UUID userId,
            List<UserProductSearchProductSnapshot> products
    ) {
        if (products.isEmpty()) {
            return Map.of();
        }
        List<UserInventoryItem> items = userInventoryItemRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        if (items.isEmpty()) {
            return products.stream()
                    .collect(Collectors.toMap(
                            UserProductSearchProductSnapshot::productKey,
                            product -> UserInventoryRecommendationSignal.none(product.productKey()),
                            (left, right) -> left,
                            LinkedHashMap::new
                    ));
        }
        return products.stream()
                .collect(Collectors.toMap(
                        UserProductSearchProductSnapshot::productKey,
                        product -> signal(InventoryCandidate.from(product), items),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    /** Resolves grouped-product inventory relationships with one inventory read for the whole window. */
    @Transactional(readOnly = true)
    public Map<String, UserInventoryRecommendationSignal> canonicalRecommendationSignals(
            UUID userId,
            List<CanonicalProduct> products
    ) {
        if (products == null || products.isEmpty()) {
            return Map.of();
        }
        List<UserInventoryItem> items = userInventoryItemRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        return products.stream().collect(Collectors.toMap(
                CanonicalProduct::key,
                product -> items.isEmpty()
                        ? UserInventoryRecommendationSignal.none(product.key())
                        : signal(InventoryCandidate.from(product), items),
                (left, right) -> left,
                LinkedHashMap::new
        ));
    }

    private List<UserInventoryItem> inventoryItems(ListUserInventoryItemsQuery query) {
        PageRequest pageRequest = PageRequest.of(query.page(), boundedLimit(
                query.limit(),
                userCollectionProperties.inventory().maxLimit()));
        if (query.category() != null && query.restockOnlyValue()) {
            return userInventoryItemRepository.findByUserIdAndCategoryAndRestockEnabledTrueOrderByUpdatedAtDesc(
                    query.userId(),
                    query.category(),
                    pageRequest
            );
        }
        if (query.category() != null) {
            return userInventoryItemRepository.findByUserIdAndCategoryOrderByUpdatedAtDesc(
                    query.userId(),
                    query.category(),
                    pageRequest
            );
        }
        if (query.restockOnlyValue()) {
            return userInventoryItemRepository.findByUserIdAndRestockEnabledTrueOrderByUpdatedAtDesc(
                    query.userId(),
                    pageRequest);
        }
        return userInventoryItemRepository.findByUserIdOrderByUpdatedAtDesc(query.userId(), pageRequest);
    }

    private void validateInventoryQuota(UUID userId) {
        int quota = userCollectionProperties.inventory().quota();
        if (userInventoryItemRepository.countByUserId(userId) >= quota) {
            throw new UserException("Inventory item quota exceeded for user " + userId);
        }
    }

    private int boundedLimit(int limit, int maxLimit) {
        return Math.min(limit, maxLimit);
    }

    private UserInventoryItem.Snapshot snapshot(CreateUserInventoryItemCommand command, String photoPath) {
        return UserInventoryItem.Snapshot.builder()
                .source(UserInventorySource.PHOTO)
                .name(command.name().trim())
                .brand(blankToNull(command.brand()))
                .category(command.category())
                .description(blankToNull(command.description()))
                .productUrl(blankToNull(command.productUrl()))
                .photoPath(photoPath)
                .quantity(command.quantity())
                .unit(blankToNull(command.unit()))
                .location(blankToNull(command.location()))
                .notes(blankToNull(command.notes()))
                .size(blankToNull(command.size()))
                .color(blankToNull(command.color()))
                .material(blankToNull(command.material()))
                .attributes(toJson(command.attributes()))
                .consumable(command.consumable())
                .restockEnabled(command.restockEnabled())
                .restockThreshold(command.restockThreshold())
                .purchasedOn(command.purchasedOn())
                .build();
    }

    private UserInventoryItem.Snapshot snapshot(
            UserInventoryItem item,
            UpdateUserInventoryItemCommand command,
            String replacementPhotoPath
    ) {
        List<String> attributes = command.attributes() == null
                ? fromJson(item.getAttributes())
                : safeList(command.attributes()).stream()
                        .filter(attribute -> attribute != null && !attribute.isBlank())
                        .map(String::trim)
                        .toList();
        return UserInventoryItem.Snapshot.builder()
                .source(item.getSource())
                .sourceProductKey(item.getSourceProductKey())
                .productHash(item.getProductHash())
                .name(patchRequiredText(command.name(), item.getName()))
                .brand(patchOptionalText(command.brand(), item.getBrand()))
                .category(firstPresent(command.category(), item.getCategory()))
                .description(patchOptionalText(command.description(), item.getDescription()))
                .imageUrl(replacementPhotoPath != null && Objects.equals(item.getImageUrl(), item.getPhotoUrl())
                        ? null : item.getImageUrl())
                .productUrl(patchOptionalText(command.productUrl(), item.getProductUrl()))
                .photoUrl(replacementPhotoPath == null ? item.getPhotoUrl() : null)
                .photoPath(replacementPhotoPath == null ? item.getPhotoPath() : replacementPhotoPath)
                .quantity(firstPresent(command.quantity(), item.getQuantity()))
                .unit(patchOptionalText(command.unit(), item.getUnit()))
                .location(patchOptionalText(command.location(), item.getLocation()))
                .notes(patchOptionalText(command.notes(), item.getNotes()))
                .size(patchOptionalText(command.size(), item.getSize()))
                .color(patchOptionalText(command.color(), item.getColor()))
                .material(patchOptionalText(command.material(), item.getMaterial()))
                .attributes(toJson(attributes))
                .consumable(firstPresent(command.consumable(), item.isConsumable()))
                .restockEnabled(firstPresent(command.restockEnabled(), item.isRestockEnabled()))
                .restockThreshold(command.restockThreshold() == null
                        ? item.getRestockThreshold() : command.restockThreshold())
                .purchasedAt(item.getPurchasedAt())
                .purchasedOn(patchOptionalDate(command.purchasedOn(), item.getPurchasedOn()))
                .provider(item.getProvider())
                .merchantIntegrationId(item.getMerchantIntegrationId())
                .externalMerchantId(item.getExternalMerchantId())
                .externalMerchantDomain(item.getExternalMerchantDomain())
                .canonicalProductKey(item.getCanonicalProductKey())
                .offerKey(item.getOfferKey())
                .sourceType(item.getSourceType())
                .sourceIdentity(item.getSourceIdentity())
                .externalProductId(item.getExternalProductId())
                .externalVariantId(item.getExternalVariantId())
                .selectedOptionsJson(item.getSelectedOptionsJson())
                .sourceCheckoutAttemptId(item.getSourceCheckoutAttemptId())
                .build();
    }

    private UserInventoryItem.Snapshot purchasedSnapshot(
            ImportPurchasedInventoryItemsCommand.PurchasedItem purchasedItem,
            UserInventoryItem existing,
            ImportPurchasedInventoryItemsCommand command
    ) {
        UserInventoryCommerceReference reference = purchasedItem.commerceReference();
        int quantity = purchasedItem.quantity() + (existing == null ? 0 : existing.getQuantity());
        UserInventoryCategory category = existing == null
                ? categoryFor(purchasedItem.name())
                : existing.getCategory();
        boolean consumable = existing == null
                ? category == UserInventoryCategory.PANTRY
                : existing.isConsumable();
        return UserInventoryItem.Snapshot.builder()
                .source(UserInventorySource.MEANT_PURCHASE)
                .sourceProductKey(purchasedItem.productKey())
                .productHash(blankToNull(purchasedItem.productHash()))
                .name(purchasedItem.name().trim())
                .brand(blankToNull(purchasedItem.brand()))
                .category(category)
                .imageUrl(blankToNull(purchasedItem.imageUrl()))
                .productUrl(blankToNull(purchasedItem.productUrl()))
                .photoPath(existingValue(existing, UserInventoryItem::getPhotoPath))
                .quantity(quantity)
                .unit(existingValue(existing, UserInventoryItem::getUnit))
                .location(existingValue(existing, UserInventoryItem::getLocation))
                .notes(existingValue(existing, UserInventoryItem::getNotes))
                .size(existingValue(existing, UserInventoryItem::getSize))
                .color(existingValue(existing, UserInventoryItem::getColor))
                .material(existingValue(existing, UserInventoryItem::getMaterial))
                .attributes(existing == null ? "[]" : existing.getAttributes())
                .consumable(consumable)
                .restockEnabled(existing != null && existing.isRestockEnabled())
                .restockThreshold(existingValue(existing, UserInventoryItem::getRestockThreshold))
                .purchasedAt(command.purchasedAt())
                .purchasedOn(existingValue(existing, UserInventoryItem::getPurchasedOn))
                .provider(reference == null
                        ? existingValue(existing, UserInventoryItem::getProvider) : reference.provider())
                .merchantIntegrationId(reference == null
                        ? existingValue(existing, UserInventoryItem::getMerchantIntegrationId)
                        : reference.merchantIntegrationId())
                .externalMerchantId(reference == null
                        ? existingValue(existing, UserInventoryItem::getExternalMerchantId)
                        : reference.externalMerchantId())
                .externalMerchantDomain(reference == null
                        ? existingValue(existing, UserInventoryItem::getExternalMerchantDomain)
                        : reference.externalMerchantDomain())
                .canonicalProductKey(reference == null
                        ? existingValue(existing, UserInventoryItem::getCanonicalProductKey)
                        : reference.canonicalProductKey())
                .offerKey(reference == null
                        ? existingValue(existing, UserInventoryItem::getOfferKey) : reference.offerKey())
                .sourceType(reference == null
                        ? existingValue(existing, UserInventoryItem::getSourceType) : reference.sourceType())
                .sourceIdentity(reference == null
                        ? existingValue(existing, UserInventoryItem::getSourceIdentity) : reference.sourceIdentity())
                .externalProductId(reference == null
                        ? existingValue(existing, UserInventoryItem::getExternalProductId)
                        : reference.externalProductId())
                .externalVariantId(reference == null
                        ? existingValue(existing, UserInventoryItem::getExternalVariantId)
                        : reference.externalVariantId())
                .selectedOptionsJson(reference == null
                        ? existingValue(existing, UserInventoryItem::getSelectedOptionsJson)
                        : toJson(reference.selectedOptions()))
                .sourceCheckoutAttemptId(command.checkoutAttemptId())
                .build();
    }

    private UserInventoryRecommendationSignal signal(InventoryCandidate candidate, List<UserInventoryItem> items) {
        UserInventoryCategory productCategory = categoryFor(candidate.evidenceText());
        Optional<UserInventoryItem> restock = items.stream()
                .filter(UserInventoryItem::isRestockEnabled)
                .filter(item -> similar(item, candidate))
                .findFirst();
        if (restock.isPresent()) {
            UserInventoryItem item = restock.get();
            return new UserInventoryRecommendationSignal(
                    candidate.productKey(),
                    UserInventoryRecommendationRelationship.RESTOCK,
                    item.getId(),
                    item.getName(),
                    "Restock candidate for " + item.getName()
            );
        }

        Optional<UserInventoryItem> duplicate = items.stream()
                .filter(item -> similar(item, candidate))
                .findFirst();
        if (duplicate.isPresent()) {
            UserInventoryItem item = duplicate.get();
            return new UserInventoryRecommendationSignal(
                    candidate.productKey(),
                    UserInventoryRecommendationRelationship.DUPLICATE,
                    item.getId(),
                    item.getName(),
                    "Looks similar to " + item.getName() + " already in inventory"
            );
        }

        Optional<UserInventoryItem> complement = items.stream()
                .filter(item -> item.getCategory() == productCategory && productCategory != UserInventoryCategory.OTHER)
                .findFirst();
        if (complement.isPresent()) {
            UserInventoryItem item = complement.get();
            return new UserInventoryRecommendationSignal(
                    candidate.productKey(),
                    UserInventoryRecommendationRelationship.COMPLEMENT,
                    item.getId(),
                    item.getName(),
                    "May complement " + item.getName() + " already in inventory"
            );
        }

        return UserInventoryRecommendationSignal.none(candidate.productKey());
    }

    private boolean similar(UserInventoryItem item, InventoryCandidate candidate) {
        if (item.getSourceProductKey() != null && item.getSourceProductKey().equals(candidate.productKey())) {
            return true;
        }
        String itemName = normalize(item.getName());
        String productName = normalize(candidate.title());
        if (itemName.isBlank() || productName.isBlank()) {
            return false;
        }
        if (productName.contains(itemName) || itemName.contains(productName)) {
            return true;
        }
        Set<String> itemTokens = tokens(itemName);
        Set<String> productTokens = tokens(productName);
        if (itemTokens.isEmpty() || productTokens.isEmpty()) {
            return false;
        }
        long overlap = itemTokens.stream()
                .filter(productTokens::contains)
                .count();
        double ratio = overlap / (double) Math.min(itemTokens.size(), productTokens.size());
        return ratio >= 0.75d;
    }

    private UserInventoryCategory categoryFor(MerchantSemanticProductResult product) {
        return categoryFor(Stream.of(
                        product.title(),
                        product.detailDescription(),
                        product.descriptionHtml()
                )
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(" ")));
    }

    private record InventoryCandidate(String productKey, String title, String evidenceText) {

        private static InventoryCandidate from(UserProductSearchProductSnapshot snapshot) {
            MerchantSemanticProductResult product = snapshot.product();
            return new InventoryCandidate(
                    snapshot.productKey(),
                    product.title(),
                    Stream.of(product.title(), product.detailDescription(), product.descriptionHtml())
                            .filter(value -> value != null && !value.isBlank())
                            .collect(Collectors.joining(" "))
            );
        }

        private static InventoryCandidate from(CanonicalProduct product) {
            String attributes = product.attributes().stream()
                    .map(attribute -> attribute.value())
                    .collect(Collectors.joining(" "));
            return new InventoryCandidate(
                    product.key(),
                    product.title(),
                    Stream.of(product.title(), product.description(), attributes)
                            .filter(value -> value != null && !value.isBlank())
                            .collect(Collectors.joining(" "))
            );
        }
    }

    private UserInventoryCategory categoryFor(String text) {
        String normalized = normalize(text);
        if (PANTRY_PATTERN.matcher(normalized).find()) {
            return UserInventoryCategory.PANTRY;
        }
        if (APPAREL_PATTERN.matcher(normalized).find()) {
            return UserInventoryCategory.APPAREL;
        }
        if (HOME_PATTERN.matcher(normalized).find()) {
            return UserInventoryCategory.HOME;
        }
        return UserInventoryCategory.OTHER;
    }

    private UserInventoryItemResult toResult(UserInventoryItem entity) {
        return new UserInventoryItemResult(
                entity.getId(),
                entity.getSource(),
                entity.getSourceProductKey(),
                entity.getProductHash(),
                entity.getName(),
                entity.getBrand(),
                entity.getCategory(),
                entity.getDescription(),
                entity.getImageUrl(),
                entity.getProductUrl(),
                entity.getPhotoUrl(),
                entity.getPhotoPath(),
                entity.getQuantity(),
                entity.getUnit(),
                entity.getLocation(),
                entity.getNotes(),
                entity.getSize(),
                entity.getColor(),
                entity.getMaterial(),
                fromJson(entity.getAttributes()),
                entity.isConsumable(),
                entity.isRestockEnabled(),
                entity.getRestockThreshold(),
                entity.getPurchasedAt(),
                entity.getPurchasedOn(),
                commerceReference(entity),
                entity.getSourceCheckoutAttemptId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private UserInventoryCommerceReference commerceReference(UserInventoryItem entity) {
        if (!hasCommerceReference(entity)) {
            return null;
        }
        try {
            return new UserInventoryCommerceReference(
                    entity.getProvider(),
                    entity.getMerchantIntegrationId(),
                    entity.getExternalMerchantId(),
                    entity.getExternalMerchantDomain(),
                    entity.getCanonicalProductKey(),
                    entity.getOfferKey(),
                    entity.getSourceType(),
                    entity.getSourceIdentity(),
                    entity.getExternalProductId(),
                    entity.getExternalVariantId(),
                    selectedOptions(entity.getSelectedOptionsJson())
            );
        } catch (IllegalArgumentException exception) {
            throw new UserException("Could not parse inventory commerce reference", exception);
        }
    }

    private boolean hasCommerceReference(UserInventoryItem entity) {
        return entity.getProvider() != null
                && !entity.getProvider().isBlank()
                && entity.getSourceType() != null
                && !entity.getSourceType().isBlank()
                && entity.getSourceIdentity() != null
                && !entity.getSourceIdentity().isBlank()
                && entity.getExternalProductId() != null
                && !entity.getExternalProductId().isBlank();
    }

    private void validateUser(EnsureUserProfileCommand profileCommand, UUID userId, String message) {
        if (!profileCommand.id().equals(userId)) {
            throw UserException.forbidden(message);
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKD)
                .toLowerCase(Locale.ROOT);
        return SPACE_PATTERN.matcher(NON_ALPHANUMERIC_PATTERN.matcher(normalized).replaceAll(" "))
                .replaceAll(" ")
                .trim();
    }

    private Set<String> tokens(String value) {
        if (value.isBlank()) {
            return Set.of();
        }
        return Stream.of(value.split(" "))
                .filter(token -> token.length() > 2)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new UserException("Could not serialize inventory item", exception);
        }
    }

    private List<String> fromJson(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, STRING_LIST_TYPE);
        } catch (JacksonException exception) {
            throw new UserException("Could not parse inventory item", exception);
        }
    }

    private List<UserInventorySelectedOption> selectedOptions(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, SELECTED_OPTION_LIST_TYPE);
        } catch (JacksonException exception) {
            throw new UserException("Could not parse inventory selected options", exception);
        }
    }

    private <T> T existingValue(
            UserInventoryItem existing,
            java.util.function.Function<UserInventoryItem, T> extractor
    ) {
        return existing == null ? null : extractor.apply(existing);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String patchRequiredText(String value, String currentValue) {
        if (value == null || value.isBlank()) {
            return currentValue;
        }
        return value.trim();
    }

    private String patchOptionalText(String value, String currentValue) {
        if (value == null) {
            return currentValue;
        }
        return blankToNull(value);
    }

    private LocalDate patchOptionalDate(String value, LocalDate currentValue) {
        if (value == null) {
            return currentValue;
        }
        if (value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new UserException("Inventory purchase date must use YYYY-MM-DD form", exception);
        }
    }

    private <T> T firstPresent(T first, T second) {
        return first != null ? first : second;
    }

    private String value(Object value) {
        return value == null ? "" : value.toString().trim();
    }

}
