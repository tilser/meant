package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.common.properties.OpenRouterProperties;
import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
import com.meant.api.module.user.constant.UserInventoryCategory;
import com.meant.api.module.user.constant.UserInventoryRecommendationRelationship;
import com.meant.api.module.user.constant.UserInventorySource;
import com.meant.api.module.user.entity.User;
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
import com.meant.api.module.user.service.dto.UserInventoryItemResult;
import com.meant.api.module.user.service.dto.UserInventoryPhotoRecognitionResult;
import com.meant.api.module.user.service.dto.UserInventoryRecommendationSignal;
import com.meant.api.module.user.service.dto.UserProductSearchProductSnapshot;
import com.meant.api.module.user.service.query.ExportUserInventoryQuery;
import com.meant.api.module.user.service.query.ListUserInventoryItemsQuery;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import tools.jackson.databind.ObjectMapper;

class UserInventoryServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000015");
    private static final Instant NOW = Instant.parse("2026-06-18T10:00:00Z");

    private FakeUserInventoryItemRepository repository;
    private FakeUserService userService;
    private FakePhotoRecognitionService photoRecognitionService;
    private UserInventoryService service;

    @BeforeEach
    void setUp() {
        repository = new FakeUserInventoryItemRepository();
        userService = new FakeUserService();
        photoRecognitionService = new FakePhotoRecognitionService();
        service = new UserInventoryService(
                userService,
                repository.proxy(),
                photoRecognitionService,
                collectionProperties(50),
                new ObjectMapper()
        );
    }

    @Test
    void createListsExportsAndDeletesManualInventoryItems() {
        UserInventoryItemResult item = service.create(profileCommand(), new CreateUserInventoryItemCommand(
                USER_ID,
                UserInventorySource.MANUAL,
                null,
                null,
                "Heavyweight Organic Cotton Tee",
                "Field Loom",
                UserInventoryCategory.APPAREL,
                "White crewneck tee",
                "https://example.test/tee.jpg",
                null,
                null,
                2,
                "pcs",
                "Wardrobe",
                "Worn weekly",
                List.of("organic cotton", "white"),
                false,
                false,
                null,
                null
        ));

        assertThat(item.source()).isEqualTo(UserInventorySource.MANUAL);
        assertThat(item.category()).isEqualTo(UserInventoryCategory.APPAREL);
        assertThat(item.quantity()).isEqualTo(2);

        List<UserInventoryItemResult> listed = service.list(
                profileCommand(),
                listQuery(UserInventoryCategory.APPAREL, false)
        );
        assertThat(listed).singleElement()
                .extracting(UserInventoryItemResult::name)
                .isEqualTo("Heavyweight Organic Cotton Tee");

        assertThat(service.export(profileCommand(), new ExportUserInventoryQuery(USER_ID)).items())
                .hasSize(1);

        service.delete(profileCommand(), new DeleteUserInventoryItemCommand(USER_ID, item.id()));

        assertThat(service.list(profileCommand(), listQuery(null, false)))
                .isEmpty();
    }

    @Test
    void createFromPhotoMergesRecognitionWithFallbackFields() {
        photoRecognitionService.nextResult = Optional.of(new UserInventoryPhotoRecognitionResult(
                "Blue Linen Shirt",
                "Northbound",
                UserInventoryCategory.APPAREL,
                "Button-down shirt on a hanger",
                List.of("linen", "blue"),
                false
        ));

        UserInventoryItemResult item = service.createFromPhoto(
                profileCommand(),
                new CreateUserInventoryPhotoItemCommand(
                        USER_ID,
                        "data:image/jpeg;base64,abc",
                        null,
                        null,
                        null,
                        null,
                        1,
                        null,
                        "Closet",
                        "Recognized from closet photo",
                        List.of("summer"),
                        null,
                        false,
                        null
                )
        );

        assertThat(item.source()).isEqualTo(UserInventorySource.PHOTO);
        assertThat(item.name()).isEqualTo("Blue Linen Shirt");
        assertThat(item.brand()).isEqualTo("Northbound");
        assertThat(item.category()).isEqualTo(UserInventoryCategory.APPAREL);
        assertThat(item.imageUrl()).isEqualTo("data:image/jpeg;base64,abc");
        assertThat(item.attributes()).containsExactly("summer", "linen", "blue");
    }

    @Test
    void updateCanClearOptionalTextFields() {
        UserInventoryItemResult created = service.create(profileCommand(), new CreateUserInventoryItemCommand(
                USER_ID,
                UserInventorySource.MANUAL,
                null,
                null,
                "Countertop Coffee Brewer",
                "Brew Works",
                UserInventoryCategory.HOME,
                "Daily coffee setup",
                "https://example.test/brewer.jpg",
                "https://example.test/brewer",
                "https://example.test/photo.jpg",
                1,
                "piece",
                "Kitchen",
                "Filter size 02",
                List.of("glass carafe"),
                false,
                false,
                null,
                null
        ));

        UserInventoryItemResult updated = service.update(profileCommand(), new UpdateUserInventoryItemCommand(
                USER_ID,
                created.id(),
                " ",
                " ",
                null,
                " ",
                " ",
                " ",
                " ",
                null,
                " ",
                " ",
                " ",
                null,
                null,
                null,
                null
        ));

        assertThat(updated.name()).isEqualTo("Countertop Coffee Brewer");
        assertThat(updated.category()).isEqualTo(UserInventoryCategory.HOME);
        assertThat(updated.quantity()).isEqualTo(1);
        assertThat(updated.brand()).isNull();
        assertThat(updated.description()).isNull();
        assertThat(updated.imageUrl()).isNull();
        assertThat(updated.productUrl()).isNull();
        assertThat(updated.photoUrl()).isNull();
        assertThat(updated.unit()).isNull();
        assertThat(updated.location()).isNull();
        assertThat(updated.notes()).isNull();
    }

    @Test
    void listCanFilterRestockEnabledPantryItems() {
        service.create(profileCommand(), manualItem("Olive Oil", UserInventoryCategory.PANTRY, true));
        service.create(profileCommand(), manualItem("Merino Sweater", UserInventoryCategory.APPAREL, false));

        List<UserInventoryItemResult> restocks = service.list(
                profileCommand(),
                listQuery(null, true)
        );

        assertThat(restocks).singleElement()
                .extracting(UserInventoryItemResult::name)
                .isEqualTo("Olive Oil");
    }

    @Test
    void importPurchasedItemsDoesNotDuplicateSamePurchaseSnapshot() {
        ImportPurchasedInventoryItemsCommand.PurchasedItem item =
                new ImportPurchasedInventoryItemsCommand.PurchasedItem(
                        "merchant.example:variant-1",
                        "hash-1",
                        "Cold-Pressed Extra Virgin Olive Oil",
                        "Casa Verde",
                        null,
                        null,
                        1,
                        NOW
                );

        service.importPurchasedItems(new ImportPurchasedInventoryItemsCommand(USER_ID, List.of(item)));
        service.importPurchasedItems(new ImportPurchasedInventoryItemsCommand(USER_ID, List.of(new ImportPurchasedInventoryItemsCommand.PurchasedItem(
                "merchant.example:variant-1",
                "hash-1",
                "Cold-Pressed Extra Virgin Olive Oil",
                "Casa Verde",
                null,
                null,
                2,
                NOW
        ))));

        List<UserInventoryItemResult> items = service.list(
                profileCommand(),
                listQuery(null, false)
        );

        assertThat(items).singleElement()
                .satisfies(result -> {
                    assertThat(result.source()).isEqualTo(UserInventorySource.MEANT_PURCHASE);
                    assertThat(result.sourceProductKey()).isEqualTo("merchant.example:variant-1");
                    assertThat(result.category()).isEqualTo(UserInventoryCategory.PANTRY);
                    assertThat(result.quantity()).isEqualTo(1);
                    assertThat(result.purchasedAt()).isEqualTo(NOW);
                });
    }

    @Test
    void importPurchasedItemsAccumulatesWhenPurchaseTimestampChanges() {
        service.importPurchasedItems(new ImportPurchasedInventoryItemsCommand(USER_ID, List.of(
                new ImportPurchasedInventoryItemsCommand.PurchasedItem(
                        "merchant.example:variant-1",
                        "hash-1",
                        "Cold-Pressed Extra Virgin Olive Oil",
                        "Casa Verde",
                        null,
                        null,
                        1,
                        NOW
                ),
                new ImportPurchasedInventoryItemsCommand.PurchasedItem(
                        "merchant.example:variant-2",
                        "hash-2",
                        "Merino Crew Sweater",
                        "Northbound",
                        null,
                        null,
                        1,
                        NOW
                )
        )));
        service.importPurchasedItems(new ImportPurchasedInventoryItemsCommand(USER_ID, List.of(
                new ImportPurchasedInventoryItemsCommand.PurchasedItem(
                        "merchant.example:variant-1",
                        "hash-1",
                        "Cold-Pressed Extra Virgin Olive Oil",
                        "Casa Verde",
                        null,
                        null,
                        2,
                        NOW.plusSeconds(60)
                )
        )));

        List<UserInventoryItemResult> items = service.list(
                profileCommand(),
                listQuery(null, false)
        );

        assertThat(items).anySatisfy(result -> {
            assertThat(result.sourceProductKey()).isEqualTo("merchant.example:variant-1");
            assertThat(result.quantity()).isEqualTo(3);
            assertThat(result.purchasedAt()).isEqualTo(NOW.plusSeconds(60));
        });
        assertThat(items).anySatisfy(result -> {
            assertThat(result.sourceProductKey()).isEqualTo("merchant.example:variant-2");
            assertThat(result.quantity()).isEqualTo(1);
        });
    }

    @Test
    void importPurchasedItemsBatchLoadsExistingKeysAndSavesChangedItemsTogether() {
        service.importPurchasedItems(new ImportPurchasedInventoryItemsCommand(USER_ID, List.of(
                new ImportPurchasedInventoryItemsCommand.PurchasedItem(
                        "merchant.example:variant-1",
                        "hash-1",
                        "Cold-Pressed Extra Virgin Olive Oil",
                        "Casa Verde",
                        null,
                        null,
                        1,
                        NOW
                )
        )));
        repository.resetCounters();

        service.importPurchasedItems(new ImportPurchasedInventoryItemsCommand(USER_ID, List.of(
                new ImportPurchasedInventoryItemsCommand.PurchasedItem(
                        "merchant.example:variant-1",
                        "hash-1",
                        "Cold-Pressed Extra Virgin Olive Oil",
                        "Casa Verde",
                        null,
                        null,
                        2,
                        NOW.plusSeconds(60)
                ),
                new ImportPurchasedInventoryItemsCommand.PurchasedItem(
                        "merchant.example:variant-2",
                        "hash-2",
                        "Merino Crew Sweater",
                        "Northbound",
                        null,
                        null,
                        1,
                        NOW
                )
        )));

        assertThat(repository.batchSourceProductLookupCount).isEqualTo(1);
        assertThat(repository.singleSourceProductLookupCount).isZero();
        assertThat(repository.saveAllCount).isEqualTo(1);
        assertThat(repository.savedInLastSaveAll).isEqualTo(2);
        assertThat(service.list(profileCommand(), listQuery(null, false)))
                .anySatisfy(result -> {
                    assertThat(result.sourceProductKey()).isEqualTo("merchant.example:variant-1");
                    assertThat(result.quantity()).isEqualTo(3);
                    assertThat(result.purchasedAt()).isEqualTo(NOW.plusSeconds(60));
                })
                .anySatisfy(result -> {
                    assertThat(result.sourceProductKey()).isEqualTo("merchant.example:variant-2");
                    assertThat(result.quantity()).isEqualTo(1);
                });
    }

    @Test
    void inventoryProfileHashUsesRepositorySignature() {
        assertThat(service.inventoryProfileHash(USER_ID)).isEqualTo("inventory:none");

        service.create(profileCommand(), manualItem("Olive Oil", UserInventoryCategory.PANTRY, true));

        assertThat(service.inventoryProfileHash(USER_ID))
                .startsWith("inventory:1:")
                .isNotEqualTo("inventory:none");
    }

    @Test
    void listAppliesPageAndLimitAtRepositoryBoundary() {
        service.create(profileCommand(), manualItem("Olive Oil", UserInventoryCategory.PANTRY, true));
        service.create(profileCommand(), manualItem("Merino Sweater", UserInventoryCategory.APPAREL, false));
        service.create(profileCommand(), manualItem("Countertop Brewer", UserInventoryCategory.HOME, false));

        List<UserInventoryItemResult> firstPage = service.list(
                profileCommand(),
                new ListUserInventoryItemsQuery(USER_ID, null, false, 0, 2)
        );
        List<UserInventoryItemResult> secondPage = service.list(
                profileCommand(),
                new ListUserInventoryItemsQuery(USER_ID, null, false, 1, 2)
        );

        assertThat(firstPage).hasSize(2);
        assertThat(secondPage).hasSize(1);
    }

    @Test
    void createRejectsNewItemsPastUserQuota() {
        UserInventoryService quotaService = new UserInventoryService(
                userService,
                repository.proxy(),
                photoRecognitionService,
                collectionProperties(1),
                new ObjectMapper()
        );

        quotaService.create(profileCommand(), manualItem("Olive Oil", UserInventoryCategory.PANTRY, true));

        assertThatThrownBy(() -> quotaService.create(
                        profileCommand(),
                        manualItem("Merino Sweater", UserInventoryCategory.APPAREL, false)))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("Inventory item quota exceeded");
    }

    @Test
    void recommendationSignalsClassifyRestocksDuplicatesComplementsAndNone() {
        UserInventoryItemResult oil = service.create(profileCommand(), manualItem(
                "Cold-Pressed Extra Virgin Olive Oil",
                UserInventoryCategory.PANTRY,
                true
        ));
        service.create(profileCommand(), manualItem(
                "Heavyweight Organic Cotton Tee",
                UserInventoryCategory.APPAREL,
                false
        ));

        List<UserProductSearchProductSnapshot> products = List.of(
                snapshot("merchant.example:oil", "Cold-Pressed Extra Virgin Olive Oil"),
                snapshot("merchant.example:tee", "Heavyweight Organic Cotton Tee"),
                snapshot("merchant.example:sweater", "Merino Crew Sweater"),
                snapshot("merchant.example:hub", "USB-C Hub")
        );

        var signals = service.recommendationSignals(USER_ID, products);

        assertThat(signals.get("merchant.example:oil"))
                .extracting(UserInventoryRecommendationSignal::relationship, UserInventoryRecommendationSignal::inventoryItemId)
                .containsExactly(UserInventoryRecommendationRelationship.RESTOCK, oil.id());
        assertThat(signals.get("merchant.example:tee").relationship())
                .isEqualTo(UserInventoryRecommendationRelationship.DUPLICATE);
        assertThat(signals.get("merchant.example:sweater").relationship())
                .isEqualTo(UserInventoryRecommendationRelationship.COMPLEMENT);
        assertThat(signals.get("merchant.example:hub").relationship())
                .isEqualTo(UserInventoryRecommendationRelationship.NONE);
    }

    private CreateUserInventoryItemCommand manualItem(
            String name,
            UserInventoryCategory category,
            boolean restockEnabled
    ) {
        return new CreateUserInventoryItemCommand(
                USER_ID,
                UserInventorySource.MANUAL,
                null,
                null,
                name,
                null,
                category,
                null,
                null,
                null,
                null,
                1,
                null,
                null,
                null,
                List.of(),
                category == UserInventoryCategory.PANTRY,
                restockEnabled,
                restockEnabled ? 1 : null,
                null
        );
    }

    private EnsureUserProfileCommand profileCommand() {
        return new EnsureUserProfileCommand(USER_ID, "inventory@example.com", "Inventory", "User");
    }

    private ListUserInventoryItemsQuery listQuery(UserInventoryCategory category, boolean restockOnly) {
        return new ListUserInventoryItemsQuery(USER_ID, category, restockOnly, 0, 50);
    }

    private UserCollectionProperties collectionProperties(int inventoryQuota) {
        return new UserCollectionProperties(
                new UserCollectionProperties.SavedProducts(50, 100, 500, 50, 20),
                new UserCollectionProperties.Inventory(50, 100, inventoryQuota)
        );
    }

    private UserProductSearchProductSnapshot snapshot(String productKey, String title) {
        MerchantSemanticProductResult product = new MerchantSemanticProductResult(
                UUID.randomUUID(),
                "merchant.example",
                "Merchant",
                "https://merchant.example/mcp",
                1,
                0.9d,
                0.9d,
                productKey.substring(productKey.indexOf(':') + 1),
                title,
                title,
                "https://merchant.example/products/" + title.replace(" ", "-"),
                null,
                1000L,
                1000L,
                "USD",
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                true,
                null,
                title,
                null,
                List.of(),
                List.of(),
                "10.00",
                "10.00",
                "USD",
                1,
                false,
                List.of(),
                "variant-1",
                "Default",
                List.of(),
                "10.00",
                "USD",
                null,
                null,
                true,
                1,
                0.9d,
                1
        );
        return new UserProductSearchProductSnapshot(productKey, "hash-" + title, product);
    }

    static class FakeUserService extends UserService {

        FakeUserService() {
            super(null);
        }

        @Override
        public User ensureProfile(EnsureUserProfileCommand command) {
            return null;
        }
    }

    static class FakePhotoRecognitionService extends UserInventoryPhotoRecognitionService {

        private Optional<UserInventoryPhotoRecognitionResult> nextResult = Optional.empty();

        FakePhotoRecognitionService() {
            super(null, openRouterProperties(), new ObjectMapper());
        }

        @Override
        public Optional<UserInventoryPhotoRecognitionResult> recognize(CreateUserInventoryPhotoItemCommand command) {
            return nextResult;
        }

        private static OpenRouterProperties openRouterProperties() {
            return new OpenRouterProperties(
                    "https://openrouter.test/api/v1",
                    "",
                    "Meant",
                    new OpenRouterProperties.Models("test", "test", "test", "test")
            );
        }
    }

    static class FakeUserInventoryItemRepository {

        private final List<UserInventoryItem> items = new ArrayList<>();
        private int singleSourceProductLookupCount;
        private int batchSourceProductLookupCount;
        private int saveAllCount;
        private int savedInLastSaveAll;

        UserInventoryItemRepository proxy() {
            return (UserInventoryItemRepository) Proxy.newProxyInstance(
                    UserInventoryItemRepository.class.getClassLoader(),
                    new Class<?>[]{UserInventoryItemRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "save" -> save((UserInventoryItem) args[0]);
                        case "saveAll" -> saveAll((Iterable<UserInventoryItem>) args[0]);
                        case "findByUserIdOrderByUpdatedAtDesc" -> args.length == 1
                                ? byUser((UUID) args[0])
                                : page(byUser((UUID) args[0]), (Pageable) args[1]);
                        case "findByUserIdAndCategoryOrderByUpdatedAtDesc" ->
                                pageIfRequested(byUser((UUID) args[0]).stream()
                                                .filter(item -> item.getCategory() == args[1])
                                                .toList(),
                                        args);
                        case "findByUserIdAndRestockEnabledTrueOrderByUpdatedAtDesc" ->
                                pageIfRequested(byUser((UUID) args[0]).stream()
                                                .filter(UserInventoryItem::isRestockEnabled)
                                                .toList(),
                                        args);
                        case "findByUserIdAndCategoryAndRestockEnabledTrueOrderByUpdatedAtDesc" ->
                                pageIfRequested(byUser((UUID) args[0]).stream()
                                                .filter(item -> item.getCategory() == args[1])
                                                .filter(UserInventoryItem::isRestockEnabled)
                                                .toList(),
                                        args);
                        case "countByUserId" -> (long) byUser((UUID) args[0]).size();
                        case "findMaxUpdatedAtByUserId" ->
                                byUser((UUID) args[0]).stream()
                                        .map(UserInventoryItem::getUpdatedAt)
                                        .max(Comparator.naturalOrder());
                        case "findByIdAndUserId" ->
                                items.stream()
                                        .filter(item -> item.getId().equals(args[0]) && item.getUserId().equals(args[1]))
                                        .findFirst();
                        case "findByUserIdAndSourceAndSourceProductKey" ->
                                findByUserIdAndSourceAndSourceProductKey(
                                        (UUID) args[0],
                                        (UserInventorySource) args[1],
                                        (String) args[2]);
                        case "findByUserIdAndSourceAndSourceProductKeyIn" ->
                                findByUserIdAndSourceAndSourceProductKeyIn(
                                        (UUID) args[0],
                                        (UserInventorySource) args[1],
                                        (Collection<String>) args[2]);
                        case "deleteByIdAndUserId" -> deleteByIdAndUserId((UUID) args[0], (UUID) args[1]);
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }

        private void resetCounters() {
            singleSourceProductLookupCount = 0;
            batchSourceProductLookupCount = 0;
            saveAllCount = 0;
            savedInLastSaveAll = 0;
        }

        private UserInventoryItem save(UserInventoryItem item) {
            items.removeIf(existing -> existing.getId().equals(item.getId()));
            items.add(item);
            return item;
        }

        private List<UserInventoryItem> saveAll(Iterable<UserInventoryItem> nextItems) {
            List<UserInventoryItem> saved = new ArrayList<>();
            nextItems.forEach(item -> saved.add(save(item)));
            saveAllCount++;
            savedInLastSaveAll = saved.size();
            return saved;
        }

        private Optional<UserInventoryItem> findByUserIdAndSourceAndSourceProductKey(
                UUID userId,
                UserInventorySource source,
                String sourceProductKey
        ) {
            singleSourceProductLookupCount++;
            return items.stream()
                    .filter(item -> item.getUserId().equals(userId))
                    .filter(item -> item.getSource() == source)
                    .filter(item -> item.getSourceProductKey() != null
                            && item.getSourceProductKey().equals(sourceProductKey))
                    .findFirst();
        }

        private List<UserInventoryItem> findByUserIdAndSourceAndSourceProductKeyIn(
                UUID userId,
                UserInventorySource source,
                Collection<String> sourceProductKeys
        ) {
            batchSourceProductLookupCount++;
            Set<String> requestedKeys = new LinkedHashSet<>(sourceProductKeys);
            return items.stream()
                    .filter(item -> item.getUserId().equals(userId))
                    .filter(item -> item.getSource() == source)
                    .filter(item -> requestedKeys.contains(item.getSourceProductKey()))
                    .toList();
        }

        private List<UserInventoryItem> byUser(UUID userId) {
            return items.stream()
                    .filter(item -> item.getUserId().equals(userId))
                    .sorted(Comparator.comparing(UserInventoryItem::getUpdatedAt).reversed())
                    .toList();
        }

        private List<UserInventoryItem> pageIfRequested(List<UserInventoryItem> source, Object[] args) {
            return args.length > 2 && args[2] instanceof Pageable pageable ? page(source, pageable) : source;
        }

        private List<UserInventoryItem> page(List<UserInventoryItem> source, Pageable pageable) {
            int start = (int) pageable.getOffset();
            if (start >= source.size()) {
                return List.of();
            }
            int end = Math.min(start + pageable.getPageSize(), source.size());
            return source.subList(start, end);
        }

        private long deleteByIdAndUserId(UUID id, UUID userId) {
            boolean removed = items.removeIf(item -> item.getId().equals(id) && item.getUserId().equals(userId));
            return removed ? 1L : 0L;
        }
    }
}
