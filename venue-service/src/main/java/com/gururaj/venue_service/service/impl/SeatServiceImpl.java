package com.gururaj.venue_service.service.impl;

import com.gururaj.venue_service.dto.SeatBulkCreateRequest;
import com.gururaj.venue_service.dto.SeatBulkCreateResponse;
import com.gururaj.venue_service.dto.SeatBulkRecordResponse;
import com.gururaj.venue_service.dto.SeatCreateRequest;
import com.gururaj.venue_service.dto.SeatLayoutRequest;
import com.gururaj.venue_service.dto.SeatResponse;
import com.gururaj.venue_service.dto.SeatUpdateRequest;
import com.gururaj.venue_service.entity.Screen;
import com.gururaj.venue_service.entity.Seat;
import com.gururaj.venue_service.exception.DuplicateResourceException;
import com.gururaj.venue_service.exception.ResourceNotFoundException;
import com.gururaj.venue_service.mapper.SeatMapper;
import com.gururaj.venue_service.repository.ScreenRepository;
import com.gururaj.venue_service.repository.SeatRepository;
import com.gururaj.venue_service.service.SeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeatServiceImpl implements SeatService {

    private final SeatRepository seatRepository;
    private final ScreenRepository screenRepository;
    private final SeatMapper seatMapper;

    @Override
    @Transactional
    public SeatResponse createSeat(SeatCreateRequest request) {
        Screen screen = getScreen(request.screenId());
        ensureSeatDoesNotExist(request.seatNumber(), screen.getId());

        Seat seat = seatMapper.toEntity(request);
        seat.setScreen(screen);
        Seat savedSeat = seatRepository.save(seat);
        increaseScreenSeatCount(screen);
        return seatMapper.toResponse(savedSeat);
    }

    @Override
    @Transactional
    public SeatBulkCreateResponse createSeats(SeatBulkCreateRequest request) {
        Screen screen = getScreen(request.screenId());
        Set<String> processedSeatNumbers = new HashSet<>();
        List<SeatBulkRecordResponse> records = new ArrayList<>();
        int successCount = 0;

        for (int index = 0; index < request.seats().size(); index++) {
            SeatLayoutRequest seatRequest = request.seats().get(index);
            String validationError = validateSeatLayout(seatRequest);

            if (validationError != null) {
                records.add(failedRecord(index, seatRequest, validationError));
                continue;
            }

            String normalizedSeatNumber = seatRequest.seatNumber().trim().toLowerCase();
            if (!processedSeatNumbers.add(normalizedSeatNumber)) {
                records.add(failedRecord(index, seatRequest, "Duplicate seat number in request"));
                continue;
            }

            if (seatRepository.existsBySeatNumberIgnoreCaseAndScreenId(seatRequest.seatNumber().trim(), screen.getId())) {
                records.add(failedRecord(index, seatRequest, "Seat already exists in this screen"));
                continue;
            }

            Seat savedSeat = seatRepository.save(toSeat(seatRequest, screen));
            increaseScreenSeatCount(screen);
            successCount++;
            records.add(new SeatBulkRecordResponse(
                    index,
                    savedSeat.getSeatNumber(),
                    "SUCCESS",
                    "Seat created successfully",
                    seatMapper.toResponse(savedSeat)
            ));
        }

        return new SeatBulkCreateResponse(
                screen.getId(),
                request.seats().size(),
                successCount,
                request.seats().size() - successCount,
                records
        );
    }

    @Override
    public List<SeatResponse> getAllSeats(Long screenId) {
        List<Seat> seats = screenId == null ? seatRepository.findAll() : seatRepository.findByScreenIdOrderByRowNameAscSeatIndexAsc(screenId);
        return seats.stream().map(seatMapper::toResponse).toList();
    }

    @Override
    public SeatResponse getSeatById(Long id) {
        return seatMapper.toResponse(getSeat(id));
    }

    @Override
    @Transactional
    public SeatResponse updateSeat(SeatUpdateRequest request) {
        Seat seat = getSeat(request.id());
        Screen screen = request.screenId() == null ? seat.getScreen() : getScreen(request.screenId());
        String seatNumber = valueOrCurrent(request.seatNumber(), seat.getSeatNumber());

        if (!seat.getSeatNumber().equalsIgnoreCase(seatNumber) || !seat.getScreen().getId().equals(screen.getId())) {
            ensureSeatDoesNotExist(seatNumber, screen.getId());
        }

        seat.setSeatNumber(seatNumber);
        seat.setRowName(valueOrCurrent(request.rowName(), seat.getRowName()));
        seat.setSeatIndex(request.seatIndex() == null ? seat.getSeatIndex() : request.seatIndex());
        seat.setSeatType(request.seatType() == null ? seat.getSeatType() : request.seatType());
        seat.setScreen(screen);
        return seatMapper.toResponse(seat);
    }

    @Override
    @Transactional
    public void deleteSeat(Long id) {
        Seat seat = getSeat(id);
        Screen screen = seat.getScreen();
        seatRepository.delete(seat);
        decreaseScreenSeatCount(screen);
    }

    private Seat getSeat(Long id) {
        return seatRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Seat not found with id: " + id));
    }

    private Screen getScreen(Long id) {
        return screenRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Screen not found with id: " + id));
    }

    private void ensureSeatDoesNotExist(String seatNumber, Long screenId) {
        if (seatRepository.existsBySeatNumberIgnoreCaseAndScreenId(seatNumber.trim(), screenId)) {
            throw new DuplicateResourceException("Seat already exists in this screen");
        }
    }

    private void increaseScreenSeatCount(Screen screen) {
        screen.setTotalSeats(screen.getTotalSeats() + 1);
    }

    private void decreaseScreenSeatCount(Screen screen) {
        screen.setTotalSeats(Math.max(0, screen.getTotalSeats() - 1));
    }

    private Seat toSeat(SeatLayoutRequest request, Screen screen) {
        Seat seat = new Seat();
        seat.setSeatNumber(request.seatNumber().trim());
        seat.setRowName(request.rowName().trim());
        seat.setSeatIndex(request.seatIndex());
        seat.setSeatType(request.seatType());
        seat.setScreen(screen);
        return seat;
    }

    private SeatBulkRecordResponse failedRecord(int index, SeatLayoutRequest request, String message) {
        return new SeatBulkRecordResponse(index, request == null ? null : request.seatNumber(), "FAILED", message, null);
    }

    private String validateSeatLayout(SeatLayoutRequest request) {
        if (request == null) {
            return "Seat record is required";
        }
        if (request.seatNumber() == null || request.seatNumber().isBlank()) {
            return "Seat number is required";
        }
        if (request.seatNumber().trim().length() > 20) {
            return "Seat number must be 20 characters or fewer";
        }
        if (request.rowName() == null || request.rowName().isBlank()) {
            return "Row name is required";
        }
        if (request.rowName().trim().length() > 10) {
            return "Row name must be 10 characters or fewer";
        }
        if (request.seatIndex() == null) {
            return "Seat index is required";
        }
        if (request.seatIndex() < 0) {
            return "Seat index cannot be negative";
        }
        if (request.seatType() == null) {
            return "Seat type is required";
        }
        return null;
    }

    private String valueOrCurrent(String incoming, String current) {
        return incoming == null || incoming.isBlank() ? current : incoming.trim();
    }
}
