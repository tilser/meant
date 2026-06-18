package com.meant.api.common.config;

import com.meant.api.common.properties.RestClientProperties;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
class RestClientConfiguration {

    @Bean
    RestClient.Builder restClientBuilder(RestClientProperties restClientProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(restClientProperties.connectTimeoutMilliseconds()));
        requestFactory.setReadTimeout(Duration.ofMillis(restClientProperties.readTimeoutMilliseconds()));
        return RestClient.builder().requestFactory(requestFactory);
    }
}
