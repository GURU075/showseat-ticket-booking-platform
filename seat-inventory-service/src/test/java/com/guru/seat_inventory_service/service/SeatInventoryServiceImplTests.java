package com.guru.seat_inventory_service.service;

import com.guru.seat_inventory_service.client.InventoryCatalogClient;
import com.guru.seat_inventory_service.dto.BlockUnblockRequest;
import com.guru.seat_inventory_service.dto.LockActionRequest;
import com.guru.seat_inventory_service.dto.LockSeatsRequest;
import com.guru.seat_inventory_service.entity.SeatStatus;
import com.guru.seat_inventory_service.entity.ShowSeat;
import com.guru.seat_inventory_service.exception.ExternalServiceException;
import com.guru.seat_inventory_service.exception.ResourceNotFoundException;
import com.guru.seat_inventory_service.exception.SeatConflictException;
import com.guru.seat_inventory_service.lock.SeatLockDetails;
import com.guru.seat_inventory_service.lock.SeatLockStore;
import com.guru.seat_inventory_service.mapper.SeatMapper;
import com.guru.seat_inventory_service.repository.ShowSeatRepository;
import com.guru.seat_inventory_service.service.impl.SeatBookingWriter;
import com.guru.seat_inventory_service.service.impl.SeatInventoryServiceImpl;
import com.guru.seat_inventory_service.service.impl.SeatInventoryWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeatInventoryServiceImplTests {

    @Mock private ShowSeatRepository showSeatRepository;
    @Mock private InventoryCatalogClient inventoryCatalogClient;
    @Mock private SeatInventoryWriter seatInventoryWriter;
    @Mock private SeatLockStore seatLockStore;
    @Mock private SeatBookingWriter seatBookingWriter;

    private SeatInventoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SeatInventoryServiceImpl(
                showSeatRepository,
                new SeatMapper(),
                inventoryCatalogClient,
                seatInventoryWriter,
                seatLockStore,
                seatBookingWriter
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
        when(seatInventoryWriter.create(eq(101L), anyList())).thenAnswer(invocation ->
                invocation.<List<String>>getArgument(1).stream().map(this::availableSeat).toList()
        );

        var response = service.createSeats(101L);

        assertEquals(List.of("A1", "B1"), response.stream().map(seat -> seat.seatNumber()).toList());
        verify(seatInventoryWriter).create(101L, List.of("A1", "B1"));
    }

    @Test
    void rejectsDuplicateInventoryCreation() {
        when(showSeatRepository.existsByShowId(101L)).thenReturn(true);
        assertThrows(SeatConflictException.class, () -> service.createSeats(101L));
        verifyNoInteractions(inventoryCatalogClient, seatInventoryWriter);
    }

    @Test
    void rejectsInventoryCreationForCancelledShow() {
        when(inventoryCatalogClient.getShow(101L))
                .thenReturn(new InventoryCatalogClient.ShowSummary(101L, 1L, 10L, "CANCELLED"));
        assertThrows(SeatConflictException.class, () -> service.createSeats(101L));
        verifyNoInteractions(seatInventoryWriter);
    }

    @Test
    void doesNotWriteInventoryWhenExternalServiceFails() {
        when(inventoryCatalogClient.getShow(101L))
                .thenThrow(new ExternalServiceException("show-service is unavailable"));
        assertThrows(ExternalServiceException.class, () -> service.createSeats(101L));
        verifyNoInteractions(seatInventoryWriter);
    }

    @Test
    void overlaysRedisLocksWhenViewingInventory() {
        when(showSeatRepository.findByShowIdOrderBySeatNumberAsc(101L))
                .thenReturn(List.of(availableSeat("A1"), availableSeat("A2"), bookedSeat("A3")));
        when(seatLockStore.findLockedSeatNumbers(101L, List.of("A1", "A2")))
                .thenReturn(Set.of("A1"));

        var response = service.getSeats(101L, null);

        assertEquals(List.of(SeatStatus.LOCKED, SeatStatus.AVAILABLE, SeatStatus.BOOKED),
                response.stream().map(seat -> seat.status()).toList());
    }

    @Test
    void filtersUsingEffectiveRedisStatus() {
        when(showSeatRepository.findByShowIdOrderBySeatNumberAsc(101L))
                .thenReturn(List.of(availableSeat("A1"), availableSeat("A2")));
        when(seatLockStore.findLockedSeatNumbers(101L, List.of("A1", "A2")))
                .thenReturn(Set.of("A1"));

        var response = service.getSeats(101L, SeatStatus.AVAILABLE);

        assertEquals(List.of("A2"), response.stream().map(seat -> seat.seatNumber()).toList());
    }

    @Test
    void locksAllSeatsAtomicallyInRedisWithoutChangingDatabaseStatus() {
        ShowSeat a1 = availableSeat("A1");
        ShowSeat a2 = availableSeat("A2");
        Instant expiresAt = Instant.parse("2026-06-30T19:05:00Z");
        when(showSeatRepository.findRequestedSeatsForUpdate(101L, List.of("A1", "A2")))
                .thenReturn(List.of(a1, a2));
        when(seatLockStore.acquire(101L, 501L, List.of("A1", "A2")))
                .thenReturn(lockDetails(List.of("A1", "A2"), expiresAt));

        var response = service.lockSeats(new LockSeatsRequest(101L, 501L, List.of("A2", "A1")));

        assertEquals("LOCK-123", response.lockId());
        assertEquals(expiresAt, response.expiresAt());
        assertEquals(SeatStatus.LOCKED, response.status());
        assertEquals(SeatStatus.AVAILABLE, a1.getStatus());
        assertEquals(SeatStatus.AVAILABLE, a2.getStatus());
    }

    @Test
    void rejectsWholeLockWhenOnePermanentSeatIsUnavailable() {
        ShowSeat available = availableSeat("A1");
        when(showSeatRepository.findRequestedSeatsForUpdate(101L, List.of("A1", "A2")))
                .thenReturn(List.of(available, bookedSeat("A2")));

        assertThrows(SeatConflictException.class, () ->
                service.lockSeats(new LockSeatsRequest(101L, 501L, List.of("A1", "A2"))));

        verifyNoInteractions(seatLockStore);
        assertEquals(SeatStatus.AVAILABLE, available.getStatus());
    }

    @Test
    void rejectsLockWhenARequestedSeatDoesNotExist() {
        when(showSeatRepository.findRequestedSeatsForUpdate(101L, List.of("A1", "Z9")))
                .thenReturn(List.of(availableSeat("A1")));
        assertThrows(ResourceNotFoundException.class, () ->
                service.lockSeats(new LockSeatsRequest(101L, 501L, List.of("A1", "Z9"))));
        verifyNoInteractions(seatLockStore);
    }

    @Test
    void confirmsOwnedRedisLockThenBooksDatabaseSeatsAndCleansUpLock() {
        SeatLockDetails lock = lockDetails(List.of("A1", "A2"), Instant.now().plusSeconds(60));
        when(seatLockStore.verifyAndExtend(101L, 501L, "LOCK-123")).thenReturn(lock);
        when(seatBookingWriter.confirm(101L, List.of("A1", "A2")))
                .thenReturn(List.of(bookedSeat("A1"), bookedSeat("A2")));
        when(seatLockStore.release(101L, 501L, "LOCK-123")).thenReturn(lock);

        var response = service.confirmSeats(new LockActionRequest(101L, 501L, "LOCK-123"));

        assertEquals(SeatStatus.BOOKED, response.status());
        assertEquals(List.of("A1", "A2"), response.seatNumbers());
        var order = inOrder(seatLockStore, seatBookingWriter);
        order.verify(seatLockStore).verifyAndExtend(101L, 501L, "LOCK-123");
        order.verify(seatBookingWriter).confirm(101L, List.of("A1", "A2"));
        order.verify(seatLockStore).release(101L, 501L, "LOCK-123");
    }

    @Test
    void rejectsConfirmWhenRedisOwnershipValidationFails() {
        when(seatLockStore.verifyAndExtend(101L, 999L, "LOCK-123"))
                .thenThrow(new SeatConflictException("not owned"));
        assertThrows(SeatConflictException.class, () ->
                service.confirmSeats(new LockActionRequest(101L, 999L, "LOCK-123")));
        verifyNoInteractions(seatBookingWriter);
    }

    @Test
    void bookingRemainsSuccessfulIfRedisCleanupFailsAfterDatabaseCommit() {
        SeatLockDetails lock = lockDetails(List.of("A1"), Instant.now().plusSeconds(60));
        when(seatLockStore.verifyAndExtend(101L, 501L, "LOCK-123")).thenReturn(lock);
        when(seatBookingWriter.confirm(101L, List.of("A1"))).thenReturn(List.of(bookedSeat("A1")));
        when(seatLockStore.release(101L, 501L, "LOCK-123"))
                .thenThrow(new ExternalServiceException("Redis unavailable"));

        var response = service.confirmSeats(new LockActionRequest(101L, 501L, "LOCK-123"));

        assertEquals(SeatStatus.BOOKED, response.status());
    }

    @Test
    void releasesOwnedRedisLockWithoutWritingDatabase() {
        SeatLockDetails lock = lockDetails(List.of("A1"), Instant.now().plusSeconds(60));
        when(seatLockStore.release(101L, 501L, "LOCK-123")).thenReturn(lock);

        var response = service.releaseSeats(new LockActionRequest(101L, 501L, "LOCK-123"));

        assertEquals(SeatStatus.AVAILABLE, response.status());
        assertEquals(List.of("A1"), response.seatNumbers());
        verifyNoInteractions(showSeatRepository, seatBookingWriter);
    }

    @Test
    void blocksAvailableSeatsWhenTheyAreNotRedisLocked() {
        ShowSeat a1 = availableSeat("A1");
        ShowSeat a2 = availableSeat("A2");
        when(showSeatRepository.findRequestedSeatsForUpdate(101L, List.of("A1", "A2")))
                .thenReturn(List.of(a1, a2));
        when(seatLockStore.findLockedSeatNumbers(101L, List.of("A1", "A2"))).thenReturn(Set.of());

        var response = service.blockSeats(new BlockUnblockRequest(101L, List.of("A2", "A1")));

        assertEquals(SeatStatus.BLOCKED, response.status());
        assertEquals(SeatStatus.BLOCKED, a1.getStatus());
        assertEquals(SeatStatus.BLOCKED, a2.getStatus());
    }

    @Test
    void rejectsBlockWhenAnySeatIsRedisLocked() {
        ShowSeat a1 = availableSeat("A1");
        ShowSeat a2 = availableSeat("A2");
        when(showSeatRepository.findRequestedSeatsForUpdate(101L, List.of("A1", "A2")))
                .thenReturn(List.of(a1, a2));
        when(seatLockStore.findLockedSeatNumbers(101L, List.of("A1", "A2"))).thenReturn(Set.of("A1"));

        assertThrows(SeatConflictException.class, () ->
                service.blockSeats(new BlockUnblockRequest(101L, List.of("A1", "A2"))));

        assertEquals(SeatStatus.AVAILABLE, a1.getStatus());
        assertEquals(SeatStatus.AVAILABLE, a2.getStatus());
    }

    @Test
    void unblocksBlockedSeats() {
        ShowSeat seat = ShowSeat.builder().showId(101L).seatNumber("A1").status(SeatStatus.BLOCKED).build();
        when(showSeatRepository.findRequestedSeatsForUpdate(101L, List.of("A1"))).thenReturn(List.of(seat));

        var response = service.unblockSeats(new BlockUnblockRequest(101L, List.of("A1")));

        assertEquals(SeatStatus.AVAILABLE, response.status());
        assertEquals(SeatStatus.AVAILABLE, seat.getStatus());
        verifyNoInteractions(seatLockStore);
    }

    private SeatLockDetails lockDetails(List<String> seatNumbers, Instant expiresAt) {
        return new SeatLockDetails("LOCK-123", 101L, 501L, seatNumbers, expiresAt);
    }

    private ShowSeat availableSeat(String seatNumber) {
        return ShowSeat.builder().showId(101L).seatNumber(seatNumber).status(SeatStatus.AVAILABLE).build();
    }

    private ShowSeat bookedSeat(String seatNumber) {
        return ShowSeat.builder().showId(101L).seatNumber(seatNumber).status(SeatStatus.BOOKED).build();
    }

    private InventoryCatalogClient.ShowSummary scheduledShow() {
        return new InventoryCatalogClient.ShowSummary(101L, 1L, 10L, "SCHEDULED");
    }
}
