package com.meant.api.module.user.service;

import static com.meant.api.common.util.CollectionUtils.safeList;

import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
import com.meant.api.module.user.constant.UserInventoryCategory;
import com.meant.api.module.user.constant.UserInventoryRecommendationRelationship;
import com.meant.api.module.user.constant.UserInventorySource;
import com.meant.api.module.user.entity.UserInventoryItem;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.repository.UserInventoryItemRepository;
import com.meant.api.module.user.service.command.CreateUserInventoryItemCommand;
import com.meant.api.module.user.service.command.CreateUserInventoryPhotoItemCommand;
import com.meant.api.module.user.service.command.DeleteUserInventoryItemCommand;
import com.meant.api.module.user.service.command.ImportPurchasedInventoryItemsCommand;
import com.meant.api.module.user.service.command.UpdateUserInventoryItemCommand;
import com.meant.api.module.user.service.command.UpsertUserCommand;
import com.meant.api.module.user.service.dto.UserInventoryExportResult;
import com.meant.api.module.user.service.dto.UserInventoryItemResult;
import com.meant.api.module.user.service.dto.UserInventoryPhotoRecognitionResult;
import com.meant.api.module.user.service.dto.UserInventoryRecommendationSignal;
import com.meant.api.module.user.service.dto.UserProductSearchProductSnapshot;
import com.meant.api.module.user.service.query.ExportUserInventoryQuery;
import com.meant.api.module.user.service.query.ListUserInventoryItemsQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
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
    private final ObjectMapper objectMapper;

    @Transactional
    public List<UserInventoryItemResult> list(
            @NotNull @Valid UpsertUserCommand upsertCommand,
            @NotNull @Valid ListUserInventoryItemsQuery query
    ) {
        validateUser(upsertCommand, query.userId(), "Inventory user does not match authenticated user");
        userService.upsert(upsertCommand);
        return inventoryItems(query).stream()
                .map(this::toResult)
                .toList();
    }

    @Transactional
    public UserInventoryExportResult export(
            @NotNull @Valid UpsertUserCommand upsertCommand,
            @NotNull @Valid ExportUserInventoryQuery query
    ) {
        validateUser(upsertCommand, query.userId(), "Inventory export user does not match authenticated user");
        userService.upsert(upsertCommand);
        return new UserInventoryExportResult(
                Instant.now(),
                userInventoryItemRepository.findByUserIdOrderByUpdatedAtDesc(query.userId()).stream()
                        .map(this::toResult)
                        .toList()
        );
    }

    @Transactional
    public UserInventoryItemResult create(
            @NotNull @Valid UpsertUserCommand upsertCommand,
            @NotNull @Valid CreateUserInventoryItemCommand command
    ) {
        validateUser(upsertCommand, command.userId(), "Inventory item user does not match authenticated user");
        userService.upsert(upsertCommand);
        Instant now = Instant.now();
        UserInventoryItem item = UserInventoryItem.create(command.userId(), snapshot(command), now);
        return toResult(userInventoryItemRepository.save(item));
    }

    @Transactional
    public UserInventoryItemResult createFromPhoto(
            @NotNull @Valid UpsertUserCommand upsertCommand,
            @NotNull @Valid CreateUserInventoryPhotoItemCommand command
    ) {
        validateUser(upsertCommand, command.userId(), "Inventory photo user does not match authenticated user");
        userService.upsert(upsertCommand);
        Optional<UserInventoryPhotoRecognitionResult> recognition =
                userInventoryPhotoRecognitionService.recognize(command);
        Instant now = Instant.now();
        UserInventoryItem item = UserInventoryItem.create(command.userId(), photoSnapshot(command, recognition), now);
        return toResult(userInventoryItemRepository.save(item));
    }

    @Transactional
    public UserInventoryItemResult update(
            @NotNull @Valid UpsertUserCommand upsertCommand,
            @NotNull @Valid UpdateUserInventoryItemCommand command
    ) {
        validateUser(upsertCommand, command.userId(), "Inventory item user does not match authenticated user");
        userService.upsert(upsertCommand);
        UserInventoryItem item = userInventoryItemRepository
                .findByIdAndUserId(command.itemId(), command.userId())
                .orElseThrow(() -> UserException.notFound("Inventory item not found: " + command.itemId()));
        Instant now = Instant.now();
        item.replaceSnapshot(snapshot(item, command), now);
        return toResult(userInventoryItemRepository.save(item));
    }

    @Transactional
    public void delete(
            @NotNull @Valid UpsertUserCommand upsertCommand,
            @NotNull @Valid DeleteUserInventoryItemCommand command
    ) {
        validateUser(upsertCommand, command.userId(), "Inventory item user does not match authenticated user");
        userService.upsert(upsertCommand);
        long deleted = userInventoryItemRepository.deleteByIdAndUserId(command.itemId(), command.userId());
        if (deleted == 0) {
            throw UserException.notFound("Inventory item not found: " + command.itemId());
        }
    }

    @Transactional
    public void importPurchasedItems(@NotNull @Valid ImportPurchasedInventoryItemsCommand command) {
        Instant now = Instant.now();
        for (ImportPurchasedInventoryItemsCommand.PurchasedItem purchasedItem : command.items()) {
            UserInventoryItem existing = userInventoryItemRepository
                    .findByUserIdAndSourceAndSourceProductKey(
                            command.userId(),
                            UserInventorySource.MEANT_PURCHASE,
                            purchasedItem.productKey()
                    )
                    .orElse(null);
            UserInventoryItem.Snapshot snapshot = purchasedSnapshot(purchasedItem, existing, now);
            UserInventoryItem item = existing == null
                    ? UserInventoryItem.create(command.userId(), snapshot, now)
                    : existing.replaceSnapshot(snapshot, now);
            userInventoryItemRepository.save(item);
        }
    }

    @Transactional(readOnly = true)
    public String inventoryProfileHash(UUID userId) {
        List<UserInventoryItem> items = userInventoryItemRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        if (items.isEmpty()) {
            return "inventory:none";
        }
        return "inventory:" + sha256(items.stream()
                .sorted(Comparator.comparing(UserInventoryItem::getId))
                .map(item -> Stream.of(
                                item.getId(),
                                item.getSource(),
                                item.getSourceProductKey(),
                                item.getName(),
                                item.getBrand(),
                                item.getCategory(),
                                item.getQuantity(),
                                item.isConsumable(),
                                item.isRestockEnabled(),
                                item.getRestockThreshold(),
                                item.getUpdatedAt()
                        )
                        .map(this::value)
                        .collect(Collectors.joining("|")))
                .collect(Collectors.joining("\n")));
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
                        product -> signal(product, items),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private List<UserInventoryItem> inventoryItems(ListUserInventoryItemsQuery query) {
        if (query.category() != null && query.restockOnlyValue()) {
            return userInventoryItemRepository.findByUserIdAndCategoryAndRestockEnabledTrueOrderByUpdatedAtDesc(
                    query.userId(),
                    query.category()
            );
        }
        if (query.category() != null) {
            return userInventoryItemRepository.findByUserIdAndCategoryOrderByUpdatedAtDesc(
                    query.userId(),
                    query.category()
            );
        }
        if (query.restockOnlyValue()) {
            return userInventoryItemRepository.findByUserIdAndRestockEnabledTrueOrderByUpdatedAtDesc(query.userId());
        }
        return userInventoryItemRepository.findByUserIdOrderByUpdatedAtDesc(query.userId());
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
                command.purchasedAt()
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
                firstPresent(blankToNull(command.name()), item.getName()),
                firstPresent(blankToNull(command.brand()), item.getBrand()),
                firstPresent(command.category(), item.getCategory()),
                firstPresent(blankToNull(command.description()), item.getDescription()),
                firstPresent(blankToNull(command.imageUrl()), item.getImageUrl()),
                firstPresent(blankToNull(command.productUrl()), item.getProductUrl()),
                firstPresent(blankToNull(command.photoUrl()), item.getPhotoUrl()),
                firstPresent(command.quantity(), item.getQuantity()),
                firstPresent(blankToNull(command.unit()), item.getUnit()),
                firstPresent(blankToNull(command.location()), item.getLocation()),
                firstPresent(blankToNull(command.notes()), item.getNotes()),
                toJson(attributes),
                firstPresent(command.consumable(), item.isConsumable()),
                firstPresent(command.restockEnabled(), item.isRestockEnabled()),
                command.restockThreshold() == null ? item.getRestockThreshold() : command.restockThreshold(),
                item.getPurchasedAt()
        );
    }

    private UserInventoryItem.Snapshot purchasedSnapshot(
            ImportPurchasedInventoryItemsCommand.PurchasedItem purchasedItem,
            UserInventoryItem existing,
            Instant now
    ) {
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
                purchasedItem.purchasedAt() == null ? now : purchasedItem.purchasedAt()
        );
    }

    private UserInventoryRecommendationSignal signal(
            UserProductSearchProductSnapshot snapshot,
            List<UserInventoryItem> items
    ) {
        UserInventoryCategory productCategory = categoryFor(snapshot.product());
        Optional<UserInventoryItem> restock = items.stream()
                .filter(UserInventoryItem::isRestockEnabled)
                .filter(item -> similar(item, snapshot))
                .findFirst();
        if (restock.isPresent()) {
            UserInventoryItem item = restock.get();
            return new UserInventoryRecommendationSignal(
                    snapshot.productKey(),
                    UserInventoryRecommendationRelationship.RESTOCK,
                    item.getId(),
                    item.getName(),
                    "Restock candidate for " + item.getName()
            );
        }

        Optional<UserInventoryItem> duplicate = items.stream()
                .filter(item -> similar(item, snapshot))
                .findFirst();
        if (duplicate.isPresent()) {
            UserInventoryItem item = duplicate.get();
            return new UserInventoryRecommendationSignal(
                    snapshot.productKey(),
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
                    snapshot.productKey(),
                    UserInventoryRecommendationRelationship.COMPLEMENT,
                    item.getId(),
                    item.getName(),
                    "May complement " + item.getName() + " already in inventory"
            );
        }

        return UserInventoryRecommendationSignal.none(snapshot.productKey());
    }

    private boolean similar(UserInventoryItem item, UserProductSearchProductSnapshot snapshot) {
        if (item.getSourceProductKey() != null && item.getSourceProductKey().equals(snapshot.productKey())) {
            return true;
        }
        String itemName = normalize(item.getName());
        String productName = normalize(snapshot.product().title());
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
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
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

    private void validateUser(UpsertUserCommand upsertCommand, UUID userId, String message) {
        if (!upsertCommand.id().equals(userId)) {
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

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
