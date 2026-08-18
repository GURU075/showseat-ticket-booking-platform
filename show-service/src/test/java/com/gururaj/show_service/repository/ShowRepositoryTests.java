package com.gururaj.show_service.repository;

import com.gururaj.show_service.entity.Show;
import com.gururaj.show_service.entity.ShowStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class ShowRepositoryTests {

    @Autowired
    private ShowRepository showRepository;

    private Show existing;

    @BeforeEach
    void setUp() {
        existing = showRepository.saveAndFlush(Show.builder()
                .eventId("event-1")
                .venueId(1L)
                .screenId(10L)
                .startTime(LocalDateTime.parse("2026-07-25T18:00:00"))
                .endTime(LocalDateTime.parse("2026-07-25T21:00:00"))
                .basePrice(new BigDecimal("250.00"))
                .status(ShowStatus.SCHEDULED)
                .build());
    }

    @Test
    void detectsPartialOverlap() {
        assertTrue(hasOverlap("2026-07-25T20:00:00", "2026-07-25T22:00:00", null));
    }

    @Test
    void allowsShowStartingExactlyWhenPreviousShowEnds() {
        assertFalse(hasOverlap("2026-07-25T21:00:00", "2026-07-25T23:00:00", null));
    }

    @Test
    void excludesCurrentShowDuringUpdate() {
        assertFalse(hasOverlap("2026-07-25T18:00:00", "2026-07-25T21:00:00", existing.getId()));
    }

    @Test
    void ignoresCancelledShows() {
        existing.setStatus(ShowStatus.CANCELLED);
        showRepository.saveAndFlush(existing);

        assertFalse(hasOverlap("2026-07-25T20:00:00", "2026-07-25T22:00:00", null));
    }

    private boolean hasOverlap(String start, String end, Long excludedId) {
        return showRepository.existsOverlappingShow(
                10L,
                LocalDateTime.parse(start),
                LocalDateTime.parse(end),
                ShowStatus.CANCELLED,
                excludedId
        );
    }
}
