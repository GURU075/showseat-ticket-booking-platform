package com.gururaj.show_service.service.impl;

import com.gururaj.show_service.client.CatalogClient;
import com.gururaj.show_service.dto.CreateShowRequest;
import com.gururaj.show_service.dto.ShowResponse;
import com.gururaj.show_service.dto.UpdateShowRequest;
import com.gururaj.show_service.entity.Show;
import com.gururaj.show_service.entity.ShowStatus;
import com.gururaj.show_service.exception.InvalidReferenceException;
import com.gururaj.show_service.exception.ResourceNotFoundException;
import com.gururaj.show_service.exception.ShowConflictException;
import com.gururaj.show_service.mapper.ShowMapper;
import com.gururaj.show_service.repository.ShowRepository;
import com.gururaj.show_service.service.ShowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShowServiceImpl implements ShowService {

    private final ShowRepository showRepository;
    private final ShowMapper showMapper;
    private final CatalogClient catalogClient;
    private final Clock clock;

    @Override
    @Transactional
    public ShowResponse createShow(CreateShowRequest request) {
        validateTimeRange(request.startTime(), request.endTime());
        validateReferences(request.eventId(), request.venueId(), request.screenId());

        showRepository.lockScreenSchedule(request.screenId());
        ensureScreenAvailable(request.screenId(), request.startTime(), request.endTime(), null);

        return showMapper.toResponse(showRepository.save(showMapper.toEntity(request)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShowResponse> getShows(Long eventId, Long screenId) {
        List<Show> shows;
        if (eventId != null && screenId != null) {
            shows = showRepository.findByEventIdAndScreenIdOrderByStartTimeAsc(eventId, screenId);
        } else if (eventId != null) {
            shows = showRepository.findByEventIdOrderByStartTimeAsc(eventId);
        } else if (screenId != null) {
            shows = showRepository.findByScreenIdOrderByStartTimeAsc(screenId);
        } else {
            shows = showRepository.findAllByOrderByStartTimeAsc();
        }
        return shows.stream().map(showMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ShowResponse getShowById(Long id) {
        return showMapper.toResponse(findShow(id));
    }

    @Override
    @Transactional
    public ShowResponse updateShow(Long id, UpdateShowRequest request) {
        validateTimeRange(request.startTime(), request.endTime());
        validateReferences(request.eventId(), request.venueId(), request.screenId());

        Show show = findShow(id);
        lockScreensInStableOrder(show.getScreenId(), request.screenId());
        ensureScreenAvailable(request.screenId(), request.startTime(), request.endTime(), id);

        show.setEventId(request.eventId());
        show.setVenueId(request.venueId());
        show.setScreenId(request.screenId());
        show.setStartTime(request.startTime());
        show.setEndTime(request.endTime());
        show.setBasePrice(request.basePrice());

        return showMapper.toResponse(showRepository.save(show));
    }

    private void validateReferences(String eventId, Long venueId, Long screenId) {
        catalogClient.verifyEventExists(eventId);
        catalogClient.verifyVenueExists(venueId);
        CatalogClient.ScreenSummary screen = catalogClient.getScreen(screenId);
        if (!venueId.equals(screen.venueId())) {
            throw new InvalidReferenceException(
                    "Screen " + screenId + " does not belong to venue " + venueId
            );
        }
    }

    private void validateTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (!startTime.isBefore(endTime)) {
            throw new ShowConflictException("Start time must be before end time");
        }
        if (!startTime.isAfter(LocalDateTime.now(clock))) {
            throw new ShowConflictException("Show start time must be in the future");
        }
    }

    private void ensureScreenAvailable(
            Long screenId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Long excludedShowId
    ) {
        if (showRepository.existsOverlappingShow(
                screenId,
                startTime,
                endTime,
                ShowStatus.CANCELLED,
                excludedShowId
        )) {
            throw new ShowConflictException(
                    "Screen " + screenId + " already has a show during the requested time"
            );
        }
    }

    private Show findShow(Long id) {
        return showRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Show not found with id: " + id));
    }

    private void lockScreensInStableOrder(Long currentScreenId, Long requestedScreenId) {
        showRepository.lockScreenSchedule(Math.min(currentScreenId, requestedScreenId));
        if (!currentScreenId.equals(requestedScreenId)) {
            showRepository.lockScreenSchedule(Math.max(currentScreenId, requestedScreenId));
        }
    }
}
