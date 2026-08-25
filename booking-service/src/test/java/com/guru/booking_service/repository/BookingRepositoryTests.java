package com.guru.booking_service.repository;

import com.guru.booking_service.domain.Booking;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:booking_repository;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
class BookingRepositoryTests {

    @Autowired
    private BookingRepository repository;

    @Test
    void storesSeatsAndFindsBookingByIdempotencyKey() {
        Booking booking = Booking.creating(
                10L, 20L, List.of("A1", "A2"), "checkout-1", "a".repeat(64), Instant.now()
        );
        repository.saveAndFlush(booking);

        Booking loaded = repository.findByUserIdAndIdempotencyKey(10L, "checkout-1").orElseThrow();

        assertThat(loaded.getSeatNumbers()).containsExactly("A1", "A2");
        assertThat(loaded.getStatus().name()).isEqualTo("CREATING");
    }
}
