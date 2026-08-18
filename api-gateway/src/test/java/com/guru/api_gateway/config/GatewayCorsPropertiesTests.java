package com.guru.api_gateway.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class GatewayCorsPropertiesTests {

    @Test
    void acceptsExplicitHttpAndHttpsOrigins() {
        GatewayCorsProperties properties = new GatewayCorsProperties(
                List.of("http://localhost:5173", "https://app.showseat.example"),
                false,
                Duration.ofHours(1)
        );

        assertThat(properties.allowedOrigins()).containsExactly(
                "http://localhost:5173",
                "https://app.showseat.example"
        );
    }

    @Test
    void rejectsWildcardOrigin() {
        assertThatIllegalArgumentException().isThrownBy(() -> new GatewayCorsProperties(
                List.of("*"),
                false,
                Duration.ofHours(1)
        ));
    }

    @Test
    void rejectsOriginContainingAPath() {
        assertThatIllegalArgumentException().isThrownBy(() -> new GatewayCorsProperties(
                List.of("https://app.showseat.example/dashboard"),
                false,
                Duration.ofHours(1)
        ));
    }

    @Test
    void rejectsNegativeMaxAge() {
        assertThatIllegalArgumentException().isThrownBy(() -> new GatewayCorsProperties(
                List.of("https://app.showseat.example"),
                false,
                Duration.ofSeconds(-1)
        ));
    }
}
