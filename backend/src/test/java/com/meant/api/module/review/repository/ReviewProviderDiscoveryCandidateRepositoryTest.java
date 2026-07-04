package com.meant.api.module.review.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.review.constant.ReviewProductIdType;
import com.meant.api.module.review.constant.ReviewProviderStatus;
import com.meant.api.module.review.constant.ReviewProviderType;
import com.meant.api.module.review.entity.ReviewProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReviewProviderDiscoveryCandidateRepositoryTest {

    @Test
    void claimCandidatesUsesJpqlAndCreatesLeaseForMerchantWithoutProvider() {
        UUID merchantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Instant now = Instant.parse("2026-07-03T12:00:00Z");
        Instant claimExpiresAt = Instant.parse("2026-07-03T12:05:00Z");
        CandidateQuery candidateQuery = new CandidateQuery(List.of(merchant(merchantId, "merchant.example", now)));
        ProviderRepositoryStub providerRepository = new ProviderRepositoryStub(Optional.empty());
        ReviewProviderDiscoveryCandidateRepository repository =
                new ReviewProviderDiscoveryCandidateRepository(candidateQuery.entityManager(), providerRepository.proxy());

        var claimed = repository.claimCandidates(now, claimExpiresAt, 10);

        assertThat(claimed)
                .extracting(candidate -> candidate.merchantId())
                .containsExactly(merchantId);
        assertThat(candidateQuery.jpql()).contains("from Merchant merchant");
        assertThat(candidateQuery.resultType()).isEqualTo(Merchant.class);
        assertThat(candidateQuery.maxResults()).isEqualTo(10);
        assertThat(candidateQuery.lockMode()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(candidateQuery.hints()).containsEntry("jakarta.persistence.lock.timeout", -2);
        ReviewProvider provider = providerRepository.savedProvider();
        assertThat(provider.getMerchantId()).isEqualTo(merchantId);
        assertThat(provider.getMerchantDomain()).isEqualTo("merchant.example");
        assertThat(provider.getProvider()).isEqualTo(ReviewProviderType.UNKNOWN);
        assertThat(provider.getStatus()).isEqualTo(ReviewProviderStatus.FAILED_RETRYABLE);
        assertThat(provider.getProductIdType()).isEqualTo(ReviewProductIdType.UNKNOWN);
        assertThat(provider.getNextCheckAt()).isEqualTo(claimExpiresAt);
    }

    @Test
    void claimCandidatesPreservesExistingProviderConfigurationDuringClaimLease() {
        UUID merchantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Instant now = Instant.parse("2026-07-03T12:00:00Z");
        Instant claimExpiresAt = Instant.parse("2026-07-03T12:05:00Z");
        CandidateQuery candidateQuery = new CandidateQuery(List.of(merchant(merchantId, "merchant.example", now)));
        ReviewProvider provider = ReviewProvider.builder()
                .merchantId(merchantId)
                .merchantDomain("old.example")
                .provider(ReviewProviderType.KLAVIYO)
                .status(ReviewProviderStatus.FAILED_RETRYABLE)
                .providerKey("company-1")
                .productIdType(ReviewProductIdType.SHOPIFY_NUMERIC_ID)
                .sourceUrl("https://merchant.example/products/tee")
                .evidence("klaviyo")
                .nextCheckAt(now.minusSeconds(1))
                .errorMessage("Previous retryable failure")
                .createdAt(now.minusSeconds(3600))
                .updatedAt(now.minusSeconds(60))
                .build();
        ProviderRepositoryStub providerRepository = new ProviderRepositoryStub(Optional.of(provider));
        ReviewProviderDiscoveryCandidateRepository repository =
                new ReviewProviderDiscoveryCandidateRepository(candidateQuery.entityManager(), providerRepository.proxy());

        repository.claimCandidates(now, claimExpiresAt, 10);

        assertThat(provider.getMerchantDomain()).isEqualTo("merchant.example");
        assertThat(provider.getStatus()).isEqualTo(ReviewProviderStatus.FAILED_RETRYABLE);
        assertThat(provider.getProvider()).isEqualTo(ReviewProviderType.KLAVIYO);
        assertThat(provider.getProviderKey()).isEqualTo("company-1");
        assertThat(provider.getProductIdType()).isEqualTo(ReviewProductIdType.SHOPIFY_NUMERIC_ID);
        assertThat(provider.getSourceUrl()).isEqualTo("https://merchant.example/products/tee");
        assertThat(provider.getEvidence()).isEqualTo("klaviyo");
        assertThat(provider.getErrorMessage()).isNull();
        assertThat(provider.getNextCheckAt()).isEqualTo(claimExpiresAt);
        assertThat(providerRepository.savedProvider()).isNull();
    }

    private Merchant merchant(UUID id, String domain, Instant updatedAt) {
        return Merchant.builder()
                .id(id)
                .domain(domain)
                .active(true)
                .updatedAt(updatedAt)
                .build();
    }

    private static class CandidateQuery {

        private final List<Merchant> merchants;
        private final EntityManager entityManager;
        private final TypedQuery<Merchant> query;
        private final Map<String, Object> hints = new LinkedHashMap<>();
        private String jpql;
        private Class<?> resultType;
        private int maxResults;
        private LockModeType lockMode;

        private CandidateQuery(List<Merchant> merchants) {
            this.merchants = merchants;
            this.query = typedQueryProxy();
            this.entityManager = entityManagerProxy();
        }

        private EntityManager entityManager() {
            return entityManager;
        }

        private String jpql() {
            return jpql;
        }

        private Class<?> resultType() {
            return resultType;
        }

        private int maxResults() {
            return maxResults;
        }

        private LockModeType lockMode() {
            return lockMode;
        }

        private Map<String, Object> hints() {
            return hints;
        }

        private EntityManager entityManagerProxy() {
            return (EntityManager) Proxy.newProxyInstance(
                    EntityManager.class.getClassLoader(),
                    new Class<?>[] {EntityManager.class},
                    this::invokeEntityManager
            );
        }

        private Object invokeEntityManager(Object proxy, Method method, Object[] args) {
            if ("createQuery".equals(method.getName()) && args != null && args.length == 2) {
                jpql = (String) args[0];
                resultType = (Class<?>) args[1];
                return query;
            }
            return defaultValue(proxy, method);
        }

        @SuppressWarnings("unchecked")
        private TypedQuery<Merchant> typedQueryProxy() {
            return (TypedQuery<Merchant>) Proxy.newProxyInstance(
                    TypedQuery.class.getClassLoader(),
                    new Class<?>[] {TypedQuery.class},
                    this::invokeTypedQuery
            );
        }

        private Object invokeTypedQuery(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "setParameter" -> proxy;
                case "setMaxResults" -> {
                    maxResults = (Integer) args[0];
                    yield proxy;
                }
                case "setLockMode" -> {
                    lockMode = (LockModeType) args[0];
                    yield proxy;
                }
                case "setHint" -> {
                    hints.put((String) args[0], args[1]);
                    yield proxy;
                }
                case "getResultList" -> merchants;
                default -> defaultValue(proxy, method);
            };
        }
    }

    private static class ProviderRepositoryStub {

        private final Optional<ReviewProvider> provider;
        private final ReviewProviderRepository proxy;
        private ReviewProvider savedProvider;

        private ProviderRepositoryStub(Optional<ReviewProvider> provider) {
            this.provider = provider;
            this.proxy = repositoryProxy();
        }

        private ReviewProviderRepository proxy() {
            return proxy;
        }

        private ReviewProvider savedProvider() {
            return savedProvider;
        }

        private ReviewProviderRepository repositoryProxy() {
            return (ReviewProviderRepository) Proxy.newProxyInstance(
                    ReviewProviderRepository.class.getClassLoader(),
                    new Class<?>[] {ReviewProviderRepository.class},
                    this::invokeRepository
            );
        }

        private Object invokeRepository(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "findByMerchantId" -> provider;
                case "save" -> {
                    savedProvider = (ReviewProvider) args[0];
                    yield savedProvider;
                }
                default -> defaultValue(proxy, method);
            };
        }
    }

    private static Object defaultValue(Object proxy, Method method) {
        return switch (method.getName()) {
            case "toString" -> proxy.getClass().getName();
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> false;
            default -> {
                Class<?> returnType = method.getReturnType();
                if (returnType == boolean.class) {
                    yield false;
                }
                if (returnType == int.class) {
                    yield 0;
                }
                yield null;
            }
        };
    }
}
