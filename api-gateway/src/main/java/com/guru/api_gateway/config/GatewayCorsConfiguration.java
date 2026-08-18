package com.guru.api_gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayCorsProperties.class)
class GatewayCorsConfiguration {

    private static final List<String> ALLOWED_METHODS = List.of(
            "GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
    );
    private static final List<String> ALLOWED_HEADERS = List.of(
            "Accept",
            "Authorization",
            "Content-Type",
            "Idempotency-Key",
            "X-Correlation-ID"
    );
    private static final List<String> EXPOSED_HEADERS = List.of(
            "Retry-After",
            "X-Correlation-ID"
    );

    @Bean
    FilterRegistrationBean<CorsFilter> gatewayCorsFilter(
            GatewayCorsProperties properties
    ) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.allowedOrigins());
        cors.setAllowedMethods(ALLOWED_METHODS);
        cors.setAllowedHeaders(ALLOWED_HEADERS);
        cors.setExposedHeaders(EXPOSED_HEADERS);
        cors.setAllowCredentials(properties.allowCredentials());
        cors.setMaxAge(properties.maxAge());
        cors.validateAllowCredentials();

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);

        FilterRegistrationBean<CorsFilter> registration =
                new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setName("gatewayCorsFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
