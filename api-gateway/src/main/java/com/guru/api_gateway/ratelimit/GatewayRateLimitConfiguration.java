package com.guru.api_gateway.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayRateLimitProperties.class)
class GatewayRateLimitConfiguration {

    @Bean
    Cache<String, Bucket> gatewayRateLimitBuckets(GatewayRateLimitProperties properties) {
        return Caffeine.newBuilder()
                .maximumSize(properties.maxBuckets())
                .expireAfterAccess(properties.bucketExpiration())
                .build();
    }

    @Bean
    FilterRegistrationBean<GatewayRateLimitFilter> gatewayRateLimitFilter(
            GatewayRateLimitProperties properties,
            Cache<String, Bucket> gatewayRateLimitBuckets
    ) {
        FilterRegistrationBean<GatewayRateLimitFilter> registration =
                new FilterRegistrationBean<>(
                        new GatewayRateLimitFilter(properties, gatewayRateLimitBuckets)
                );
        registration.setName("gatewayRateLimitFilter");
        // Correlation ID and CORS run first, so controlled 429 responses include both.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }
}
