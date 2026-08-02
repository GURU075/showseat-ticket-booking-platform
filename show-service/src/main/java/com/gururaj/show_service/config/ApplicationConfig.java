package com.gururaj.show_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;

@Configuration
public class ApplicationConfig {

    @Bean
    Clock applicationClock(@Value("${app.time-zone}") String timeZone) {
        return Clock.system(ZoneId.of(timeZone));
    }

    @Bean("eventServiceRestClient")
    RestClient eventServiceRestClient(
            ClientTimeoutProperties timeouts,
            @Value("${clients.event-service.base-url}") String baseUrl
    ) {
        return buildClient(baseUrl, timeouts);
    }

    @Bean("venueServiceRestClient")
    RestClient venueServiceRestClient(
            ClientTimeoutProperties timeouts,
            @Value("${clients.venue-service.base-url}") String baseUrl
    ) {
        return buildClient(baseUrl, timeouts);
    }

    private RestClient buildClient(
            String baseUrl,
            ClientTimeoutProperties timeouts
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeouts.connectTimeout());
        requestFactory.setReadTimeout(timeouts.readTimeout());
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @ConfigurationProperties(prefix = "clients")
    public record ClientTimeoutProperties(Duration connectTimeout, Duration readTimeout) {
    }
}
