package com.guru.api_gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;

@ConfigurationProperties("gateway.cors")
public record GatewayCorsProperties(
        List<String> allowedOrigins,
        boolean allowCredentials,
        Duration maxAge
) {

    public GatewayCorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);

        if (allowedOrigins.isEmpty()) {
            throw new IllegalArgumentException("At least one CORS allowed origin is required");
        }
        if (allowedOrigins.stream().anyMatch(origin -> origin == null || origin.isBlank())) {
            throw new IllegalArgumentException("CORS allowed origins must not be blank");
        }
        if (allowedOrigins.contains("*")) {
            throw new IllegalArgumentException(
                    "Wildcard CORS origins are not allowed; configure trusted origins explicitly"
            );
        }
        allowedOrigins.forEach(GatewayCorsProperties::validateOrigin);
        if (maxAge == null || maxAge.isNegative()) {
            throw new IllegalArgumentException("CORS max-age must be zero or positive");
        }
    }

    private static void validateOrigin(String origin) {
        URI uri;
        try {
            uri = URI.create(origin);
        }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid CORS origin: " + origin, exception);
        }

        boolean validScheme = Set.of("http", "https").contains(uri.getScheme());
        boolean hasNoExtraComponents = (uri.getPath() == null || uri.getPath().isEmpty())
                && uri.getQuery() == null
                && uri.getFragment() == null
                && uri.getUserInfo() == null;
        if (!validScheme || uri.getHost() == null || !hasNoExtraComponents) {
            throw new IllegalArgumentException(
                    "CORS origin must contain only scheme, host, and optional port: " + origin
            );
        }
    }
}
