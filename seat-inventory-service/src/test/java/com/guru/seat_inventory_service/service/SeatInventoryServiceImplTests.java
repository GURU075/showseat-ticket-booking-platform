package com.guru.seat_inventory_service.service;

import com.guru.seat_inventory_service.dto.BlockUnblockRequest;
import com.guru.seat_inventory_service.dto.LockActionRequest;
import com.guru.seat_inventory_service.dto.LockSeatsRequest;
import com.guru.seat_inventory_service.entity.SeatStatus;
import com.guru.seat_inventory_service.entity.ShowSeat;
import com.guru.seat_inventory_service.exception.ResourceNotFoundException;
import com.guru.seat_inventory_service.exception.SeatConflictException;
import com.guru.seat_inventory_service.exception.ExternalServiceException;
import com.guru.seat_inventory_service.mapper.SeatMapper;
import com.guru.seat_inventory_service.client.InventoryCatalogClient;
import com.guru.seat_inventory_service.repository.ShowSeatRepository;
import com.guru.seat_inventory_service.service.impl.SeatInventoryServiceImpl;
import com.guru.seat_inventory_service.service.impl.SeatInventoryWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeatInventoryServiceImplTests {

    @Mock
    private ShowSeatRepository showSeatRepository;

    @Mock
    private InventoryCatalogClient inventoryCatalogClient;

    @Mock
    private SeatInventoryWriter seatInventoryWriter;

    private SeatInventoryServiceImpl seatInventoryService;

    @BeforeEach
    void setUp() {
        seatInventoryService = new SeatInventoryServiceImpl(
                showSeatRepository,
                new SeatMapper(),
                inventoryCatalogClient,
                seatInventoryWriter
        );
    }

    @Test
    void createsNormalizedSeatsFromShowAndVenueCatalogs() {
        when(inventoryCatalogClient.getShow(101L)).thenReturn(scheduledShow());
        when(inventoryCatalogClient.getScreen(10L))
                .thenReturn(new InventoryCatalogClient.ScreenSummary(10L, 1L));
        when(inventoryCatalogClient.getScreenSeats(10L)).thenReturn(List.of(
                new InventoryCatalogClient.ScreenSeatSummary("b1", 10L),
                new InventoryCatalogClient.ScreenSeatSummary(" A1 ", 10L)
        ));
        when(seatInventoryWriter.create(eq(101L), anyList())).thenAnswer(invocation -> {
            List<String> seatNumbers = invocation.getArgument(1);
            return seatNumbers.stream().map(this::availableSeat).toList();
        });

        var response = seatInventoryService.createSeats(101L);

        assertEquals(List.of("A1", "B1"), response.stream().map(seat -> seat.seatNumber()).toList());
        assertTrue(response.stream().allMatch(seat -> seat.status() == SeatStatus.AVAILABLE));
        verify(seatInventoryWriter).create(101L, List.of("A1", "B1"));
    }

    @Test
    void rejectsDuplicateInventoryCreation() {
        when(showSeatRepository.existsByShowId(101L)).thenReturn(true);

        assertThrows(
                SeatConflictException.class,
                () -> seatInventoryService.createSeats(101L)
        );

        verifyNoInteractions(inventoryCatalogClient, seatInventoryWriter);
    }

    @Test
    void rejectsInventoryCreationForCancelledShow() {
        when(inventoryCatalogClient.getShow(101L))
                .thenReturn(new InventoryCatalogClient.ShowSummary(101L, 1L, 10L, "CANCELLED"));

        SeatConflictException exception = assertThrows(
                SeatConflictException.class,
                () -> seatInventoryService.createSeats(101L)
        );

        assertTrue(exception.getMessage().contains("CANCELLED"));
        verify(inventoryCatalogClient, never()).getScreen(anyLong());
        verifyNoInteractions(seatInventoryWriter);
    }

    @Test
    void rejectsScreenFromDifferentVenue() {
        when(inventoryCatalogClient.getShow(101L)).thenReturn(scheduledShow());
        when(inventoryCatalogClient.getScreen(10L))
                .thenReturn(new InventoryCatalogClient.ScreenSummary(10L, 2L));

        assertThrows(SeatConflictException.class, () -> seatInventoryService.createSeats(101L));

        verify(inventoryCatalogClient, never()).getScreenSeats(anyLong());
        verifyNoInteractions(seatInventoryWriter);
    }

    @Test
    void rejectsScreenWithoutPhysicalSeats() {
        when(inventoryCatalogClient.getShow(101L)).thenReturn(scheduledShow());
        when(inventoryCatalogClient.getScreen(10L))
                .thenReturn(new InventoryCatalogClient.ScreenSummary(10L, 1L));
        when(inventoryCatalogClient.getScreenSeats(10L)).thenReturn(List.of());

        SeatConflictException exception = assertThrows(
                SeatConflictException.class,
                () -> seatInventoryService.createSeats(101L)
        );

        assertTrue(exception.getMessage().contains("has no seats"));
        verifyNoInteractions(seatInventoryWriter);
    }

    @Test
    void rejectsSeatReturnedForUnexpectedScreen() {
        when(inventoryCatalogClient.getShow(101L)).thenReturn(scheduledShow());
        when(inventoryCatalogClient.getScreen(10L))
                .thenReturn(new InventoryCatalogClient.ScreenSummary(10L, 1L));
        when(inventoryCatalogClient.getScreenSeats(10L)).thenReturn(List.of(
                new InventoryCatalogClient.ScreenSeatSummary("A1", 99L)
        ));

        assertThrows(ExternalServiceException.class, () -> seatInventoryService.createSeats(101L));

        verifyNoInteractions(seatInventoryWriter);
    }

    @Test
    void doesNotWriteInventoryWhenExternalServiceFails() {
        when(inventoryCatalogClient.getShow(101L))
                .thenThrow(new ExternalServiceException("show-service is unavailable"));

        assertThrows(ExternalServiceException.class, () -> seatInventoryService.createSeats(101L));

        verifyNoInteractions(seatInventoryWriter);
    }

    @Test
    void locksEveryRequestedSeatWithOneLockId() {
        ShowSeat a1 = availableSeat("A1");
        ShowSeat a2 = availableSeat("A2");
        when(showSeatRepository.findRequestedSeatsForUpdate(101L, List.of("A1", "A2")))
                .thenReturn(List.of(a1, a2));

        var response = seatInventoryService.lockSeats(
                new LockSeatsRequest(101L, 501L, List.of("A2", "A1"))
        );

        assertTrue(response.lockId().startsWith("LOCK-"));
        assertEquals(List.of("A1", "A2"), response.seatNumbers());
        assertEquals(SeatStatus.LOCKED, response.status());
        assertNull(response.expiresAt());
        assertAll(
                () -> assertEquals(response.lockId(), a1.getLockId()),
                () -> assertEquals(response.lockId(), a2.getLockId()),
                () -> assertEquals(501L, a1.getLockedByUserId()),
                () -> assertEquals(SeatStatus.LOCKED, a2.getStatus())
        );
    }

    @Test
    void rejectsWholeLockWhenOneSeatIsAlreadyUnavailable() {
        ShowSeat available = availableSeat("A1");
        ShowSeat booked = ShowSeat.builder()
                .showId(101L)
                .seatNumber("A2")
                .status(SeatStatus.BOOKED)
                .build();
        when(showSeatRepository.findRequestedSeatsForUpdate(101L, List.of("A1", "A2")))
                .thenReturn(List.of(available, booked));

        assertThrows(
                SeatConflictException.class,
                () -> seatInventoryService.lockSeats(
                        new LockSeatsRequest(101L, 501L, List.of("A1", "A2"))
                )
        );

        assertEquals(SeatStatus.AVAILABLE, available.getStatus());
    }

    @Test
    void rejectsLockWhenARequestedSeatDoesNotExist() {
        when(showSeatRepository.findRequestedSeatsForUpdate(101L, List.of("A1", "Z9")))
                .thenReturn(List.of(availableSeat("A1")));

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> seatInventoryService.lockSeats(
                        new LockSeatsRequest(101L, 501L, List.of("A1", "Z9"))
                )
        );

        assertTrue(exception.getMessage().contains("Z9"));
    }

    @Test
    void confirmsOnlyTheUserOwnedLock() {
        ShowSeat a1 = lockedSeat("A1", "LOCK-123", 501L);
        ShowSeat a2 = lockedSeat("A2", "LOCK-123", 501L);
        when(showSeatRepository.findLockForUpdate(101L, "LOCK-123")).thenReturn(List.of(a1, a2));

        var response = seatInventoryService.confirmSeats(
                new LockActionRequest(101L, 501L, "LOCK-123")
        );

        assertEquals(SeatStatus.BOOKED, response.status());
        assertAll(
                () -> assertEquals(SeatStatus.BOOKED, a1.getStatus()),
                () -> assertEquals(SeatStatus.BOOKED, a2.getStatus()),
                () -> assertNull(a1.getLockId()),
                () -> assertNull(a1.getLockedByUserId())
        );
    }

    @Test
    void rejectsConfirmFromAnotherUserWithoutChangingSeats() {
        ShowSeat seat = lockedSeat("A1", "LOCK-123", 501L);
        when(showSeatRepository.findLockForUpdate(101L, "LOCK-123")).thenReturn(List.of(seat));

        assertThrows(
                SeatConflictException.class,
                () -> seatInventoryService.confirmSeats(
                        new LockActionRequest(101L, 999L, "LOCK-123")
                )
        );

        assertEquals(SeatStatus.LOCKED, seat.getStatus());
    }

    @Test
    void releasesSeatsBackToAvailable() {
        ShowSeat seat = lockedSeat("A1", "LOCK-123", 501L);
        when(showSeatRepository.findLockForUpdate(101L, "LOCK-123")).thenReturn(List.of(seat));

        var response = seatInventoryService.releaseSeats(
                new LockActionRequest(101L, 501L, "LOCK-123")
        );

        assertEquals(SeatStatus.AVAILABLE, response.status());
        assertEquals(SeatStatus.AVAILABLE, seat.getStatus());
        assertNull(seat.getLockId());
    }

    @Test
    void blocksEveryAvailableSeat() {
        ShowSeat a1 = availableSeat("A1");
        ShowSeat a2 = availableSeat("A2");
        when(showSeatRepository.findRequestedSeatsForUpdate(101L, List.of("A1", "A2")))
                .thenReturn(List.of(a1, a2));

        var response = seatInventoryService.blockSeats(
                new BlockUnblockRequest(101L, List.of("A2", "A1"))
        );

        assertEquals(SeatStatus.BLOCKED, response.status());
        assertEquals(List.of("A1", "A2"), response.seatNumbers());
        assertAll(
                () -> assertEquals(SeatStatus.BLOCKED, a1.getStatus()),
                () -> assertEquals(SeatStatus.BLOCKED, a2.getStatus()),
                () -> assertNull(a1.getLockId()),
                () -> assertNull(a1.getLockedByUserId())
        );
    }

    @Test
    void rejectsWholeBlockWhenOneSeatIsNotAvailable() {
        ShowSeat available = availableSeat("A1");
        ShowSeat booked = ShowSeat.builder()
                .showId(101L)
                .seatNumber("A2")
                .status(SeatStatus.BOOKED)
                .build();
        when(showSeatRepository.findRequestedSeatsForUpdate(101L, List.of("A1", "A2")))
                .thenReturn(List.of(available, booked));

        SeatConflictException exception = assertThrows(
                SeatConflictException.class,
                () -> seatInventoryService.blockSeats(
                        new BlockUnblockRequest(101L, List.of("A1", "A2"))
                )
        );

        assertTrue(exception.getMessage().contains("A2 (BOOKED)"));
        assertEquals(SeatStatus.AVAILABLE, available.getStatus());
        assertEquals(SeatStatus.BOOKED, booked.getStatus());
    }

    @Test
    void unblocksEveryBlockedSeat() {
        ShowSeat a1 = blockedSeat("A1");
        ShowSeat a2 = blockedSeat("A2");
        when(showSeatRepository.findRequestedSeatsForUpdate(101L, List.of("A1", "A2")))
                .thenReturn(List.of(a1, a2));

        var response = seatInventoryService.unblockSeats(
                new BlockUnblockRequest(101L, List.of("A2", "A1"))
        );

        assertEquals(SeatStatus.AVAILABLE, response.status());
        assertEquals(List.of("A1", "A2"), response.seatNumbers());
        assertEquals(SeatStatus.AVAILABLE, a1.getStatus());
        assertEquals(SeatStatus.AVAILABLE, a2.getStatus());
    }

    @Test
    void rejectsWholeUnblockWhenOneSeatIsNotBlocked() {
        ShowSeat blocked = blockedSeat("A1");
        ShowSeat available = availableSeat("A2");
        when(showSeatRepository.findRequestedSeatsForUpdate(101L, List.of("A1", "A2")))
                .thenReturn(List.of(blocked, available));

        SeatConflictException exception = assertThrows(
                SeatConflictException.class,
                () -> seatInventoryService.unblockSeats(
                        new BlockUnblockRequest(101L, List.of("A1", "A2"))
                )
        );

        assertTrue(exception.getMessage().contains("A2 (AVAILABLE)"));
        assertEquals(SeatStatus.BLOCKED, blocked.getStatus());
        assertEquals(SeatStatus.AVAILABLE, available.getStatus());
    }

    @Test
    void rejectsBlockWhenARequestedSeatDoesNotExist() {
        when(showSeatRepository.findRequestedSeatsForUpdate(101L, List.of("A1", "Z9")))
                .thenReturn(List.of(availableSeat("A1")));

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> seatInventoryService.blockSeats(
                        new BlockUnblockRequest(101L, List.of("A1", "Z9"))
                )
        );

        assertTrue(exception.getMessage().contains("Z9"));
    }

    private ShowSeat availableSeat(String seatNumber) {
        return ShowSeat.builder()
                .showId(101L)
                .seatNumber(seatNumber)
                .status(SeatStatus.AVAILABLE)
                .build();
    }

    private ShowSeat lockedSeat(String seatNumber, String lockId, Long userId) {
        return ShowSeat.builder()
                .showId(101L)
                .seatNumber(seatNumber)
                .status(SeatStatus.LOCKED)
                .lockId(lockId)
                .lockedByUserId(userId)
                .build();
    }

    private ShowSeat blockedSeat(String seatNumber) {
        return ShowSeat.builder()
                .showId(101L)
                .seatNumber(seatNumber)
                .status(SeatStatus.BLOCKED)
                .build();
    }

    private InventoryCatalogClient.ShowSummary scheduledShow() {
        return new InventoryCatalogClient.ShowSummary(101L, 1L, 10L, "SCHEDULED");
    }
}
