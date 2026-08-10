package com.guru.seat_inventory_service.service.impl;

import com.guru.seat_inventory_service.client.InventoryCatalogClient;
import com.guru.seat_inventory_service.dto.*;
import com.guru.seat_inventory_service.entity.SeatStatus;
import com.guru.seat_inventory_service.entity.ShowSeat;
import com.guru.seat_inventory_service.exception.ResourceNotFoundException;
import com.guru.seat_inventory_service.exception.SeatConflictException;
import com.guru.seat_inventory_service.exception.ExternalServiceException;
import com.guru.seat_inventory_service.mapper.SeatMapper;
import com.guru.seat_inventory_service.repository.ShowSeatRepository;
import com.guru.seat_inventory_service.service.SeatInventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class SeatInventoryServiceImpl implements SeatInventoryService {

    private final ShowSeatRepository showSeatRepository;
    private final SeatMapper seatMapper;
    private final InventoryCatalogClient inventoryCatalogClient;
    private final SeatInventoryWriter seatInventoryWriter;

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
        List<ShowSeat> seats = status == null
                ? showSeatRepository.findByShowIdOrderBySeatNumberAsc(showId)
                : showSeatRepository.findByShowIdAndStatusOrderBySeatNumberAsc(showId, status);

        if (seats.isEmpty() && !showSeatRepository.existsByShowId(showId)) {
            throw new ResourceNotFoundException("Seat inventory not found for show " + showId);
        }

        return seats.stream().map(seatMapper::toResponse).toList();
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

        String lockId = "LOCK-" + UUID.randomUUID();
        seats.forEach(seat -> seat.lock(lockId, request.userId()));

        return new SeatLockResponse(
                lockId,
                request.showId(),
                seatNumbers(seats),
                SeatStatus.LOCKED,
                null
        );
    }

    @Override
    @Transactional
    public SeatActionResponse confirmSeats(LockActionRequest request) {
        return changeLockedSeats(request, SeatStatus.BOOKED, ShowSeat::book);
    }

    @Override
    @Transactional
    public SeatActionResponse releaseSeats(LockActionRequest request) {
        return changeLockedSeats(request, SeatStatus.AVAILABLE, ShowSeat::release);
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

        seats.forEach(transition);
        return new SeatStatusChangeResponse(
                request.showId(),
                seatNumbers(seats),
                targetStatus
        );
    }

    private SeatActionResponse changeLockedSeats(
            LockActionRequest request,
            SeatStatus targetStatus,
            Consumer<ShowSeat> transition
    ) {
        List<ShowSeat> seats = showSeatRepository.findLockForUpdate(request.showId(), request.lockId());
        if (seats.isEmpty()) {
            throw new ResourceNotFoundException(
                    "Active seat lock not found for show " + request.showId() + " and lock " + request.lockId()
            );
        }
        if (seats.stream().anyMatch(seat -> !request.userId().equals(seat.getLockedByUserId()))) {
            throw new SeatConflictException("Seat lock belongs to a different user");
        }

        List<String> seatNumbers = seatNumbers(seats);
        seats.forEach(transition);
        return new SeatActionResponse(
                request.lockId(),
                request.showId(),
                seatNumbers,
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
