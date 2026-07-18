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
import com.meant.api.module.user.service.command.CreateUserInventoryPhotoItemCommand;
import com.meant.api.module.user.service.command.DeleteUserInventoryItemCommand;
import com.meant.api.module.user.service.command.ImportPurchasedInventoryItemsCommand;
import com.meant.api.module.user.service.command.UpdateUserInventoryItemCommand;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.dto.UserInventoryExportResult;
import com.meant.api.module.user.service.dto.UserInventoryCommerceReference;
import com.meant.api.module.user.service.dto.UserInventoryItemResult;
import com.meant.api.module.user.service.dto.UserInventoryPhotoRecognitionResult;
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
import org.springframework.transaction.annotation.Propagation;
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
    private final UserInventoryPhotoRecognitionService userInventoryPhotoRecognitionService;
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
        Instant now = Instant.now();
        UserInventoryItem item = UserInventoryItem.create(command.userId(), snapshot(command), now);
        return toResult(userInventoryItemRepository.save(item));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public UserInventoryItemResult createFromPhoto(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid CreateUserInventoryPhotoItemCommand command
    ) {
        validateUser(profileCommand, command.userId(), "Inventory photo user does not match authenticated user");
        userService.ensureProfile(profileCommand);
        validateInventoryQuota(command.userId());
        Optional<UserInventoryPhotoRecognitionResult> recognition =
                userInventoryPhotoRecognitionService.recognize(command);
        Instant now = Instant.now();
        UserInventoryItem item = UserInventoryItem.create(command.userId(), photoSnapshot(command, recognition), now);
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
        Instant now = Instant.now();
        item.replaceSnapshot(snapshot(item, command), now);
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
        long itemCount = userInventoryItemRepository.countByUserId(userId);
        if (itemCount == 0) {
            return "inventory:none";
        }
        Instant lastUpdatedAt = userInventoryItemRepository.findMaxUpdatedAtByUserId(userId).orElse(null);
        return "inventory:" + itemCount + ":" + value(lastUpdatedAt);
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

    private UserInventoryItem.Snapshot snapshot(CreateUserInventoryItemCommand command) {
        return new UserInventoryItem.Snapshot(
                command.source(),
                blankToNull(command.sourceProductKey()),
                blankToNull(command.productHash()),
                command.name().trim(),
                blankToNull(command.brand()),
                command.category(),
                blankToNull(command.description()),
                blankToNull(command.imageUrl()),
                blankToNull(command.productUrl()),
                blankToNull(command.photoUrl()),
                command.quantity(),
                blankToNull(command.unit()),
                blankToNull(command.location()),
                blankToNull(command.notes()),
                toJson(command.attributes()),
                command.consumable(),
                command.restockEnabled(),
                command.restockThreshold(),
                command.purchasedAt(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private UserInventoryItem.Snapshot photoSnapshot(
            CreateUserInventoryPhotoItemCommand command,
            Optional<UserInventoryPhotoRecognitionResult> recognition
    ) {
        UserInventoryPhotoRecognitionResult recognized = recognition.orElse(null);
        UserInventoryCategory category = firstPresent(command.category(), recognized == null ? null : recognized.category());
        boolean consumable = command.consumable() == null
                ? Boolean.TRUE.equals(recognized == null ? null : recognized.consumable())
                : command.consumable();
        return new UserInventoryItem.Snapshot(
                UserInventorySource.PHOTO,
                null,
                null,
                firstPresent(blankToNull(command.name()), recognized == null ? null : recognized.name(), "Photo inventory item"),
                firstPresent(blankToNull(command.brand()), recognized == null ? null : recognized.brand()),
                category == null ? UserInventoryCategory.OTHER : category,
                firstPresent(blankToNull(command.description()), recognized == null ? null : recognized.description()),
                command.photoUrl(),
                null,
                command.photoUrl(),
                command.quantity(),
                blankToNull(command.unit()),
                blankToNull(command.location()),
                blankToNull(command.notes()),
                toJson(mergedAttributes(command.attributes(), recognized)),
                consumable,
                command.restockEnabled(),
                command.restockThreshold(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private UserInventoryItem.Snapshot snapshot(UserInventoryItem item, UpdateUserInventoryItemCommand command) {
        List<String> attributes = command.attributes() == null
                ? fromJson(item.getAttributes())
                : safeList(command.attributes()).stream()
                        .filter(attribute -> attribute != null && !attribute.isBlank())
                        .map(String::trim)
                        .toList();
        return new UserInventoryItem.Snapshot(
                item.getSource(),
                item.getSourceProductKey(),
                item.getProductHash(),
                patchRequiredText(command.name(), item.getName()),
                patchOptionalText(command.brand(), item.getBrand()),
                firstPresent(command.category(), item.getCategory()),
                patchOptionalText(command.description(), item.getDescription()),
                patchOptionalText(command.imageUrl(), item.getImageUrl()),
                patchOptionalText(command.productUrl(), item.getProductUrl()),
                patchOptionalText(command.photoUrl(), item.getPhotoUrl()),
                firstPresent(command.quantity(), item.getQuantity()),
                patchOptionalText(command.unit(), item.getUnit()),
                patchOptionalText(command.location(), item.getLocation()),
                patchOptionalText(command.notes(), item.getNotes()),
                toJson(attributes),
                firstPresent(command.consumable(), item.isConsumable()),
                firstPresent(command.restockEnabled(), item.isRestockEnabled()),
                command.restockThreshold() == null ? item.getRestockThreshold() : command.restockThreshold(),
                item.getPurchasedAt(),
                item.getProvider(),
                item.getMerchantIntegrationId(),
                item.getExternalMerchantId(),
                item.getExternalMerchantDomain(),
                item.getCanonicalProductKey(),
                item.getOfferKey(),
                item.getSourceType(),
                item.getSourceIdentity(),
                item.getExternalProductId(),
                item.getExternalVariantId(),
                item.getSelectedOptionsJson(),
                item.getSourceCheckoutAttemptId()
        );
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
        return new UserInventoryItem.Snapshot(
                UserInventorySource.MEANT_PURCHASE,
                purchasedItem.productKey(),
                blankToNull(purchasedItem.productHash()),
                purchasedItem.name().trim(),
                blankToNull(purchasedItem.brand()),
                category,
                null,
                blankToNull(purchasedItem.imageUrl()),
                blankToNull(purchasedItem.productUrl()),
                null,
                quantity,
                existing == null ? null : existing.getUnit(),
                existing == null ? null : existing.getLocation(),
                existing == null ? null : existing.getNotes(),
                existing == null ? "[]" : existing.getAttributes(),
                consumable,
                existing != null && existing.isRestockEnabled(),
                existing == null ? null : existing.getRestockThreshold(),
                command.purchasedAt(),
                reference == null ? existingValue(existing, UserInventoryItem::getProvider) : reference.provider(),
                reference == null
                        ? existingValue(existing, UserInventoryItem::getMerchantIntegrationId)
                        : reference.merchantIntegrationId(),
                reference == null
                        ? existingValue(existing, UserInventoryItem::getExternalMerchantId)
                        : reference.externalMerchantId(),
                reference == null
                        ? existingValue(existing, UserInventoryItem::getExternalMerchantDomain)
                        : reference.externalMerchantDomain(),
                reference == null
                        ? existingValue(existing, UserInventoryItem::getCanonicalProductKey)
                        : reference.canonicalProductKey(),
                reference == null ? existingValue(existing, UserInventoryItem::getOfferKey) : reference.offerKey(),
                reference == null
                        ? existingValue(existing, UserInventoryItem::getSourceType)
                        : reference.sourceType(),
                reference == null
                        ? existingValue(existing, UserInventoryItem::getSourceIdentity)
                        : reference.sourceIdentity(),
                reference == null
                        ? existingValue(existing, UserInventoryItem::getExternalProductId)
                        : reference.externalProductId(),
                reference == null
                        ? existingValue(existing, UserInventoryItem::getExternalVariantId)
                        : reference.externalVariantId(),
                reference == null
                        ? existingValue(existing, UserInventoryItem::getSelectedOptionsJson)
                        : toJson(reference.selectedOptions()),
                command.checkoutAttemptId()
        );
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
                entity.getQuantity(),
                entity.getUnit(),
                entity.getLocation(),
                entity.getNotes(),
                fromJson(entity.getAttributes()),
                entity.isConsumable(),
                entity.isRestockEnabled(),
                entity.getRestockThreshold(),
                entity.getPurchasedAt(),
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

    private List<String> mergedAttributes(
            List<String> commandAttributes,
            UserInventoryPhotoRecognitionResult recognized
    ) {
        LinkedHashSet<String> attributes = new LinkedHashSet<>();
        safeList(commandAttributes).stream()
                .filter(attribute -> attribute != null && !attribute.isBlank())
                .map(String::trim)
                .forEach(attributes::add);
        if (recognized != null) {
            safeList(recognized.attributes()).stream()
                    .filter(attribute -> attribute != null && !attribute.isBlank())
                    .map(String::trim)
                    .forEach(attributes::add);
        }
        return List.copyOf(attributes);
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

    private <T> T firstPresent(T first, T second) {
        return first != null ? first : second;
    }

    @SafeVarargs
    private <T> T firstPresent(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String value(Object value) {
        return value == null ? "" : value.toString().trim();
    }

}
