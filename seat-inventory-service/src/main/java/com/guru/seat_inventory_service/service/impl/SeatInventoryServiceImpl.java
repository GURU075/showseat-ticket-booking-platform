package com.guru.seat_inventory_service.service.impl;

import com.guru.seat_inventory_service.client.InventoryCatalogClient;
import com.guru.seat_inventory_service.dto.*;
import com.guru.seat_inventory_service.entity.SeatStatus;
import com.guru.seat_inventory_service.entity.ShowSeat;
import com.guru.seat_inventory_service.exception.ResourceNotFoundException;
import com.guru.seat_inventory_service.exception.SeatConflictException;
import com.guru.seat_inventory_service.exception.ExternalServiceException;
import com.guru.seat_inventory_service.mapper.SeatMapper;
import com.guru.seat_inventory_service.lock.SeatLockDetails;
import com.guru.seat_inventory_service.lock.SeatLockStore;
import com.guru.seat_inventory_service.repository.ShowSeatRepository;
import com.guru.seat_inventory_service.service.SeatInventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeatInventoryServiceImpl implements SeatInventoryService {

    private final ShowSeatRepository showSeatRepository;
    private final SeatMapper seatMapper;
    private final InventoryCatalogClient inventoryCatalogClient;
    private final SeatInventoryWriter seatInventoryWriter;
    private final SeatLockStore seatLockStore;
    private final SeatBookingWriter seatBookingWriter;

    @Override
    public List<SeatResponse> createSeats(Long showId) {
        if (showSeatRepository.existsByShowId(showId)) {
            throw new SeatConflictException("Seat inventory already exists for show " + showId);
        }

        InventoryCatalogClient.ShowSummary show = inventoryCatalogClient.getShow(showId);
        validateShow(showId, show);

        InventoryCatalogClient.ScreenSummary screen = inventoryCatalogClient.getScreen(show.screenId());
        validateScreen(show, screen);

        List<InventoryCatalogClient.ScreenSeatSummary> screenSeats =
                inventoryCatalogClient.getScreenSeats(show.screenId());
        if (screenSeats == null) {
            throw new ExternalServiceException("venue-service returned an empty response");
        }
        if (screenSeats.isEmpty()) {
            throw new SeatConflictException("Screen " + show.screenId() + " has no seats");
        }
        if (screenSeats.stream().anyMatch(seat -> !show.screenId().equals(seat.screenId()))) {
            throw new ExternalServiceException("venue-service returned seats for an unexpected screen");
        }

        List<String> seatNumbers = normalizeAndValidateSeatNumbers(
                screenSeats.stream().map(InventoryCatalogClient.ScreenSeatSummary::seatNumber).toList()
        );

        return seatInventoryWriter.create(showId, seatNumbers).stream()
                .map(seatMapper::toResponse)
                .toList();
    }

    private void validateShow(Long requestedShowId, InventoryCatalogClient.ShowSummary show) {
        if (show.id() == null || show.screenId() == null || show.venueId() == null || show.status() == null) {
            throw new ExternalServiceException("show-service returned incomplete show data");
        }
        if (!requestedShowId.equals(show.id())) {
            throw new ExternalServiceException("show-service returned an unexpected show id");
        }
        if (!"SCHEDULED".equals(show.status())) {
            throw new SeatConflictException(
                    "Seat inventory cannot be created for show " + requestedShowId + " in status " + show.status()
            );
        }
    }

    private void validateScreen(
            InventoryCatalogClient.ShowSummary show,
            InventoryCatalogClient.ScreenSummary screen
    ) {
        if (screen.id() == null || screen.venueId() == null) {
            throw new ExternalServiceException("venue-service returned incomplete screen data");
        }
        if (!show.screenId().equals(screen.id())) {
            throw new ExternalServiceException("venue-service returned an unexpected screen id");
        }
        if (!show.venueId().equals(screen.venueId())) {
            throw new SeatConflictException(
                    "Screen " + screen.id() + " does not belong to venue " + show.venueId()
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<SeatResponse> getSeats(Long showId, SeatStatus status) {
        if (status == SeatStatus.BOOKED || status == SeatStatus.BLOCKED) {
            List<ShowSeat> permanentSeats =
                    showSeatRepository.findByShowIdAndStatusOrderBySeatNumberAsc(showId, status);
            ensureInventoryExists(showId, permanentSeats);
            return permanentSeats.stream().map(seatMapper::toResponse).toList();
        }

        List<ShowSeat> seats = showSeatRepository.findByShowIdOrderBySeatNumberAsc(showId);
        ensureInventoryExists(showId, seats);

        List<String> availableSeatNumbers = seats.stream()
                .filter(seat -> seat.getStatus() == SeatStatus.AVAILABLE)
                .map(ShowSeat::getSeatNumber)
                .toList();
        Set<String> lockedSeatNumbers =
                seatLockStore.findLockedSeatNumbers(showId, availableSeatNumbers);

        return seats.stream()
                .map(seat -> toEffectiveResponse(seat, lockedSeatNumbers))
                .filter(response -> status == null || response.status() == status)
                .toList();
    }

    private void ensureInventoryExists(Long showId, List<ShowSeat> seats) {
        if (seats.isEmpty() && !showSeatRepository.existsByShowId(showId)) {
            throw new ResourceNotFoundException("Seat inventory not found for show " + showId);
        }
    }

    private SeatResponse toEffectiveResponse(ShowSeat seat, Set<String> lockedSeatNumbers) {
        SeatStatus effectiveStatus = seat.getStatus() == SeatStatus.AVAILABLE
                && lockedSeatNumbers.contains(seat.getSeatNumber())
                ? SeatStatus.LOCKED
                : seat.getStatus();
        return new SeatResponse(seat.getShowId(), seat.getSeatNumber(), effectiveStatus);
    }

    @Override
    @Transactional
    public SeatLockResponse lockSeats(LockSeatsRequest request) {
        List<String> requestedNumbers = normalizeAndValidateSeatNumbers(request.seatNumbers());
        List<ShowSeat> seats = showSeatRepository.findRequestedSeatsForUpdate(
                request.showId(),
                requestedNumbers
        );

        ensureEverySeatExists(request.showId(), requestedNumbers, seats);
        List<String> unavailableSeats = seats.stream()
                .filter(seat -> seat.getStatus() != SeatStatus.AVAILABLE)
                .map(seat -> seat.getSeatNumber() + " (" + seat.getStatus() + ")")
                .toList();
        if (!unavailableSeats.isEmpty()) {
            throw new SeatConflictException("Seats are not available: " + String.join(", ", unavailableSeats));
        }

        SeatLockDetails lock = seatLockStore.acquire(
                request.showId(),
                request.userId(),
                requestedNumbers
        );

        return new SeatLockResponse(
                lock.lockId(),
                request.showId(),
                lock.seatNumbers(),
                SeatStatus.LOCKED,
                lock.expiresAt()
        );
    }

    @Override
    public SeatActionResponse confirmSeats(LockActionRequest request) {
        SeatLockDetails lock = seatLockStore.verifyAndExtend(
                request.showId(),
                request.userId(),
                request.lockId()
        );
        List<ShowSeat> bookedSeats = seatBookingWriter.confirm(request.showId(), lock.seatNumbers());
        try {
            seatLockStore.release(request.showId(), request.userId(), request.lockId());
        } catch (ExternalServiceException | ResourceNotFoundException ex) {
            // PostgreSQL is already committed as BOOKED. The stale Redis keys are harmless
            // because permanent DB status wins when rendering inventory, and the keys expire.
            log.warn("Booked seats but could not remove Redis lock {}", request.lockId(), ex);
        }
        return new SeatActionResponse(
                request.lockId(),
                request.showId(),
                seatNumbers(bookedSeats),
                SeatStatus.BOOKED
        );
    }

    @Override
    public SeatActionResponse releaseSeats(LockActionRequest request) {
        SeatLockDetails released = seatLockStore.release(
                request.showId(),
                request.userId(),
                request.lockId()
        );
        return new SeatActionResponse(
                request.lockId(),
                request.showId(),
                released.seatNumbers(),
                SeatStatus.AVAILABLE
        );
    }

    @Override
    @Transactional
    public SeatStatusChangeResponse blockSeats(BlockUnblockRequest request) {
        return changeSeatsStatus(
                request,
                SeatStatus.AVAILABLE,
                SeatStatus.BLOCKED,
                "blocked",
                ShowSeat::block
        );
    }

    @Override
    @Transactional
    public SeatStatusChangeResponse unblockSeats(BlockUnblockRequest request) {
        return changeSeatsStatus(
                request,
                SeatStatus.BLOCKED,
                SeatStatus.AVAILABLE,
                "unblocked",
                ShowSeat::unblock
        );
    }

    private SeatStatusChangeResponse changeSeatsStatus(
            BlockUnblockRequest request,
            SeatStatus requiredCurrentStatus,
            SeatStatus targetStatus,
            String operation,
            Consumer<ShowSeat> transition
    ) {
        List<String> requestedNumbers = normalizeAndValidateSeatNumbers(request.seatNumbers());
        List<ShowSeat> seats = showSeatRepository.findRequestedSeatsForUpdate(
                request.showId(),
                requestedNumbers
        );
        ensureEverySeatExists(request.showId(), requestedNumbers, seats);
        List<String> conflictingSeats = seats.stream()
                .filter(seat -> seat.getStatus() != requiredCurrentStatus)
                .map(seat -> seat.getSeatNumber() + " (" + seat.getStatus() + ")")
                .toList();
        if (!conflictingSeats.isEmpty()) {
            throw new SeatConflictException(
                    "Seats cannot be " + operation + ": " + String.join(", ", conflictingSeats)
            );
        }

        if (targetStatus == SeatStatus.BLOCKED) {
            Set<String> lockedSeats = seatLockStore.findLockedSeatNumbers(
                    request.showId(),
                    requestedNumbers
            );
            if (!lockedSeats.isEmpty()) {
                throw new SeatConflictException(
                        "Locked seats cannot be blocked: " + String.join(", ", lockedSeats.stream().sorted().toList())
                );
            }
        }

        seats.forEach(transition);
        return new SeatStatusChangeResponse(
                request.showId(),
                seatNumbers(seats),
                targetStatus
        );
    }

    private void ensureEverySeatExists(
            Long showId,
            List<String> requestedNumbers,
            List<ShowSeat> foundSeats
    ) {
        List<String> foundNumbers = foundSeats.stream().map(ShowSeat::getSeatNumber).toList();
        List<String> missingNumbers = requestedNumbers.stream()
                .filter(seatNumber -> !foundNumbers.contains(seatNumber))
                .toList();
        if (!missingNumbers.isEmpty()) {
            throw new ResourceNotFoundException(
                    "Seats not found for show " + showId + ": " + String.join(", ", missingNumbers)
            );
        }
    }

    private List<String> normalizeAndValidateSeatNumbers(List<String> seatNumbers) {
        List<String> normalized = seatNumbers.stream()
                .map(this::normalizeSeatNumber)
                .sorted()
                .toList();

        if (normalized.stream().distinct().count() != normalized.size()) {
            throw new SeatConflictException("seatNumbers must not contain duplicates");
        }
        return normalized;
    }

    private String normalizeSeatNumber(String seatNumber) {
        if (seatNumber == null || seatNumber.isBlank()) {
            throw new SeatConflictException("seat number must not be blank");
        }
        String normalized = seatNumber.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("^[A-Z0-9_-]{1,20}$")) {
            throw new SeatConflictException(
                    "Invalid seat number '" + seatNumber + "'; use letters, numbers, underscore, or hyphen"
            );
        }
        return normalized;
    }

    private List<String> seatNumbers(List<ShowSeat> seats) {
        return seats.stream().map(ShowSeat::getSeatNumber).toList();
    }
}
