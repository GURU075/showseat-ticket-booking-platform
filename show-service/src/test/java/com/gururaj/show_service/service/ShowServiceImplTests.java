package com.gururaj.show_service.service;

import com.gururaj.show_service.client.CatalogClient;
import com.gururaj.show_service.dto.CreateShowRequest;
import com.gururaj.show_service.dto.UpdateShowRequest;
import com.gururaj.show_service.entity.Show;
import com.gururaj.show_service.entity.ShowStatus;
import com.gururaj.show_service.exception.InvalidReferenceException;
import com.gururaj.show_service.exception.ShowConflictException;
import com.gururaj.show_service.mapper.ShowMapper;
import com.gururaj.show_service.repository.ShowRepository;
import com.gururaj.show_service.service.impl.ShowServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShowServiceImplTests {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-23T06:30:00Z"), ZONE);

    @Mock
    private ShowRepository showRepository;

    @Mock
    private CatalogClient catalogClient;

    private ShowServiceImpl showService;

    @BeforeEach
    void setUp() {
        showService = new ShowServiceImpl(showRepository, new ShowMapper(), catalogClient, CLOCK);
    }

    @Test
    void createsShowAfterReferenceAndOverlapValidation() {
        CreateShowRequest request = createRequest("segwe2341", 1L, 10L, "2026-07-25T18:00:00", "2026-07-25T21:00:00");
        when(catalogClient.getScreen(10L)).thenReturn(new CatalogClient.ScreenSummary(10L, 1L));
        when(showRepository.existsOverlappingShow(
                eq(10L), any(), any(), eq(ShowStatus.CANCELLED), isNull()
        )).thenReturn(false);
        when(showRepository.save(any(Show.class))).thenAnswer(invocation -> {
            Show show = invocation.getArgument(0);
            show.setId(99L);
            show.setStatus(ShowStatus.SCHEDULED);
            return show;
        });

        var response = showService.createShow(request);

        assertEquals(99L, response.id());
        assertEquals(ShowStatus.SCHEDULED, response.status());
//        verify(catalogClient).verifyEventExists(1L);
        verify(catalogClient).verifyVenueExists(1L);
        verify(showRepository).lockScreenSchedule(10L);
        verify(showRepository).save(any(Show.class));
    }

    @Test
    void rejectsOverlappingShow() {
        CreateShowRequest request = createRequest("segwe2341", 1L, 10L, "2026-07-25T20:00:00", "2026-07-25T22:00:00");
        when(catalogClient.getScreen(10L)).thenReturn(new CatalogClient.ScreenSummary(10L, 1L));
        when(showRepository.existsOverlappingShow(
                eq(10L), any(), any(), eq(ShowStatus.CANCELLED), isNull()
        )).thenReturn(true);

        ShowConflictException exception = assertThrows(
                ShowConflictException.class,
                () -> showService.createShow(request)
        );

        assertTrue(exception.getMessage().contains("already has a show"));
        verify(showRepository, never()).save(any());
    }

    @Test
    void rejectsScreenFromAnotherVenue() {
        CreateShowRequest request = createRequest("segwe2341", 1L, 10L, "2026-07-25T18:00:00", "2026-07-25T21:00:00");
        when(catalogClient.getScreen(10L)).thenReturn(new CatalogClient.ScreenSummary(10L, 2L));

        assertThrows(InvalidReferenceException.class, () -> showService.createShow(request));

        verify(showRepository, never()).lockScreenSchedule(anyLong());
        verify(showRepository, never()).save(any());
    }

    @Test
    void rejectsInvalidTimeRangeBeforeCallingOtherServices() {
        CreateShowRequest request = createRequest("segwe2341", 1L, 10L, "2026-07-25T21:00:00", "2026-07-25T18:00:00");

        assertThrows(ShowConflictException.class, () -> showService.createShow(request));

        verifyNoInteractions(catalogClient, showRepository);
    }

    @Test
    void updateExcludesCurrentShowFromOverlapCheck() {
        Show existing = Show.builder()
                .id(7L)
                .eventId("segwe2341")
                .venueId(1L)
                .screenId(10L)
                .startTime(LocalDateTime.parse("2026-07-25T18:00:00"))
                .endTime(LocalDateTime.parse("2026-07-25T21:00:00"))
                .basePrice(new BigDecimal("250.00"))
                .status(ShowStatus.SCHEDULED)
                .build();
        UpdateShowRequest request = new UpdateShowRequest(
                "sdc233e",
                1L,
                10L,
                LocalDateTime.parse("2026-07-25T19:00:00"),
                LocalDateTime.parse("2026-07-25T22:00:00"),
                new BigDecimal("300.00")
        );
        when(catalogClient.getScreen(10L)).thenReturn(new CatalogClient.ScreenSummary(10L, 1L));
        when(showRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(showRepository.existsOverlappingShow(
                eq(10L), any(), any(), eq(ShowStatus.CANCELLED), eq(7L)
        )).thenReturn(false);
        when(showRepository.save(existing)).thenReturn(existing);

        var response = showService.updateShow(7L, request);

        assertEquals(LocalDateTime.parse("2026-07-25T19:00:00"), response.startTime());
        assertEquals(new BigDecimal("300.00"), response.basePrice());
        verify(showRepository).existsOverlappingShow(
                10L,
                request.startTime(),
                request.endTime(),
                ShowStatus.CANCELLED,
                7L
        );
    }

    private CreateShowRequest createRequest(
            String eventId,
            Long venueId,
            Long screenId,
            String startTime,
            String endTime
    ) {
        return new CreateShowRequest(
                eventId,
                venueId,
                screenId,
                LocalDateTime.parse(startTime),
                LocalDateTime.parse(endTime),
                new BigDecimal("250.00")
        );
    }
}
