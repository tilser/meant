package com.meant.api.module.merchant.service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Arrays;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

class MerchantClientHttpRequestFactory extends HttpComponentsClientHttpRequestFactory {

    MerchantClientHttpRequestFactory(MerchantOutboundUrlValidator merchantOutboundUrlValidator) {
        this(merchantOutboundUrlValidator, null, null);
    }

    MerchantClientHttpRequestFactory(
            MerchantOutboundUrlValidator merchantOutboundUrlValidator,
            Duration connectTimeout,
            Duration readTimeout
    ) {
        super(HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(new ValidatingDnsResolver(merchantOutboundUrlValidator))
                        .setDefaultConnectionConfig(connectionConfig(connectTimeout, readTimeout))
                        .build())
                .setDefaultRequestConfig(requestConfig(connectTimeout, readTimeout))
                .disableRedirectHandling()
                .build());
    }

    private static ConnectionConfig connectionConfig(Duration connectTimeout, Duration readTimeout) {
        ConnectionConfig.Builder builder = ConnectionConfig.custom();
        if (connectTimeout != null) {
            builder.setConnectTimeout(Timeout.of(connectTimeout));
        }
        if (readTimeout != null) {
            builder.setSocketTimeout(Timeout.of(readTimeout));
        }
        return builder.build();
    }

    private static RequestConfig requestConfig(Duration connectTimeout, Duration readTimeout) {
        RequestConfig.Builder builder = RequestConfig.custom()
                .setRedirectsEnabled(false);
        if (connectTimeout != null) {
            builder.setConnectTimeout(Timeout.of(connectTimeout));
        }
        if (readTimeout != null) {
            builder.setResponseTimeout(Timeout.of(readTimeout));
        }
        return builder.build();
    }

    private static class ValidatingDnsResolver implements DnsResolver {

        private final MerchantOutboundUrlValidator merchantOutboundUrlValidator;

        ValidatingDnsResolver(MerchantOutboundUrlValidator merchantOutboundUrlValidator) {
            this.merchantOutboundUrlValidator = merchantOutboundUrlValidator;
        }

        @Override
        public InetAddress[] resolve(String host) throws UnknownHostException {
            InetAddress[] addresses = SystemDefaultDnsResolver.INSTANCE.resolve(host);
            merchantOutboundUrlValidator.validatePublicAddresses(Arrays.asList(addresses));
            return addresses;
        }

        @Override
        public String resolveCanonicalHostname(String host) throws UnknownHostException {
            return SystemDefaultDnsResolver.INSTANCE.resolveCanonicalHostname(host);
        }
    }
}
