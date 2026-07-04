package com.meant.api.module.order.service;

import com.meant.api.module.order.constant.OrderListPagination;
import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.repository.MerchantRepository;
import com.meant.api.module.merchant.service.MerchantIdentityLinkService;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.module.merchant.service.dto.MerchantIdentityAccessTokenResult;
import com.meant.api.module.merchant.service.query.GetMerchantIdentityAccessTokenQuery;
import com.meant.api.module.order.entity.MerchantOrder;
import com.meant.api.module.order.exception.OrderException;
import com.meant.api.module.order.service.dto.OrderListResult;
import com.meant.api.module.order.service.dto.OrderResult;
import com.meant.api.module.order.service.query.GetOrderQuery;
import com.meant.api.module.order.service.query.ListOrdersQuery;
import com.meant.api.plugin.order.common.dto.UcpOrderToolResult;
import com.meant.api.plugin.order.common.service.MerchantOrderPluginDispatchService;
import com.meant.api.plugin.order.get.dto.GetOrderRequest;
import com.meant.api.plugin.support.UcpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class OrderService {

    private final OrderPersistenceService orderPersistenceService;
    private final MerchantOrderPluginDispatchService merchantOrderPluginDispatchService;
    private final MerchantIdentityLinkService merchantIdentityLinkService;
    private final MerchantRepository merchantRepository;
    private final OrderResultMapper orderResultMapper;

    public OrderListResult list(@NotNull @Valid ListOrdersQuery query) {
        PageRequest pageRequest = PageRequest.of(
                query.page(),
                Math.min(query.limit(), OrderListPagination.MAX_LIMIT)
        );
        Slice<MerchantOrder> orders = orderPersistenceService.listOrders(query.userId(), pageRequest);
        return new OrderListResult(
                orders.getContent().stream()
                        .map(orderResultMapper::summaryFrom)
                        .toList(),
                orders.getNumber(),
                orders.getSize(),
                orders.hasNext()
        );
    }

    public OrderResult get(@NotNull @Valid GetOrderQuery query) {
        MerchantOrder order = orderPersistenceService.findOrder(query.orderId(), query.userId());
        if (!query.refresh()) {
            return orderResultMapper.from(order);
        }
        return orderResultMapper.from(refresh(order, query));
    }

    private MerchantOrder refresh(MerchantOrder order, GetOrderQuery query) {
        Merchant merchant = merchantRepository.findById(order.getMerchantId())
                .orElseThrow(() -> OrderException.notFound("Merchant not found: " + order.getMerchantId()));
        MerchantIdentityAccessTokenResult token = merchantIdentityLinkService.getAccessToken(
                new GetMerchantIdentityAccessTokenQuery(query.userId(), merchant.getId())
        );
        UcpOrderToolResult result = merchantOrderPluginDispatchService.getOrder(
                provider(merchant),
                new GetOrderRequest(order.getRemoteOrderId()),
                UcpSession.start(),
                token.tokenType(),
                token.accessToken()
        );
        return orderPersistenceService.saveSnapshot(
                query.userId(),
                merchant,
                result.endpoint(),
                result.response().order(),
                result.rawResponse(),
                order.getLastWebhookId(),
                order.getLastWebhookTopic()
        );
    }

    private MerchantCartProvider provider(Merchant merchant) {
        return new MerchantCartProvider(
                merchant.getId(),
                merchant.getDomain(),
                merchant.getAdvertisedMcpEndpoint(),
                merchant.getProfileMcpEndpoint(),
                merchant.isNativeCheckoutEnabled()
        );
    }
}
