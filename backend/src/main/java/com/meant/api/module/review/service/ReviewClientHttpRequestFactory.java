package com.meant.api.module.review.service;

import com.meant.api.module.review.exception.ReviewException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Arrays;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

class ReviewClientHttpRequestFactory extends HttpComponentsClientHttpRequestFactory {

    ReviewClientHttpRequestFactory(
            ReviewOutboundUrlValidator outboundUrlValidator,
            Duration connectTimeout,
            Duration readTimeout
    ) {
        super(HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(new ValidatingDnsResolver(outboundUrlValidator))
                        .setDefaultConnectionConfig(connectionConfig(connectTimeout, readTimeout))
                        .build())
                .setRedirectStrategy(new ValidatingRedirectStrategy(outboundUrlValidator))
                .setDefaultRequestConfig(requestConfig(connectTimeout, readTimeout))
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
                .setRedirectsEnabled(true);
        if (connectTimeout != null) {
            builder.setConnectTimeout(Timeout.of(connectTimeout));
        }
        if (readTimeout != null) {
            builder.setResponseTimeout(Timeout.of(readTimeout));
        }
        return builder.build();
    }

    private static class ValidatingDnsResolver implements DnsResolver {

        private final ReviewOutboundUrlValidator outboundUrlValidator;

        ValidatingDnsResolver(ReviewOutboundUrlValidator outboundUrlValidator) {
            this.outboundUrlValidator = outboundUrlValidator;
        }

        @Override
        public InetAddress[] resolve(String host) throws UnknownHostException {
            InetAddress[] addresses = SystemDefaultDnsResolver.INSTANCE.resolve(host);
            outboundUrlValidator.validatePublicAddresses(Arrays.asList(addresses));
            return addresses;
        }

        @Override
        public String resolveCanonicalHostname(String host) throws UnknownHostException {
            return SystemDefaultDnsResolver.INSTANCE.resolveCanonicalHostname(host);
        }
    }

    private static class ValidatingRedirectStrategy extends DefaultRedirectStrategy {

        private final ReviewOutboundUrlValidator outboundUrlValidator;

        ValidatingRedirectStrategy(ReviewOutboundUrlValidator outboundUrlValidator) {
            this.outboundUrlValidator = outboundUrlValidator;
        }

        @Override
        public java.net.URI getLocationURI(
                HttpRequest request,
                HttpResponse response,
                HttpContext context
        ) throws HttpException {
            java.net.URI location = super.getLocationURI(request, response, context);
            try {
                return outboundUrlValidator.validateOutboundUrl(location);
            } catch (ReviewException exception) {
                throw new ProtocolException("Review outbound redirect URL is not allowed", exception);
            }
        }
    }
}
