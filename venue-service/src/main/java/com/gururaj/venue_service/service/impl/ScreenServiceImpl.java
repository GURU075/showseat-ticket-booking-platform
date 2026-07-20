package com.gururaj.venue_service.service.impl;

import com.gururaj.venue_service.dto.ScreenCreateRequest;
import com.gururaj.venue_service.dto.ScreenResponse;
import com.gururaj.venue_service.dto.ScreenUpdateRequest;
import com.gururaj.venue_service.entity.Screen;
import com.gururaj.venue_service.entity.Venue;
import com.gururaj.venue_service.exception.DuplicateResourceException;
import com.gururaj.venue_service.exception.ResourceNotFoundException;
import com.gururaj.venue_service.mapper.ScreenMapper;
import com.gururaj.venue_service.repository.ScreenRepository;
import com.gururaj.venue_service.repository.VenueRepository;
import com.gururaj.venue_service.service.ScreenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScreenServiceImpl implements ScreenService {

    private final ScreenRepository screenRepository;
    private final VenueRepository venueRepository;
    private final ScreenMapper screenMapper;

    @Override
    @Transactional
    public ScreenResponse createScreen(ScreenCreateRequest request) {
        Venue venue = getVenue(request.venueId());
        ensureScreenDoesNotExist(request.name(), venue.getId());

        Screen screen = screenMapper.toEntity(request);
        screen.setVenue(venue);
        return screenMapper.toResponse(screenRepository.save(screen));
    }

    @Override
    public List<ScreenResponse> getAllScreens(Long venueId) {
        List<Screen> screens = venueId == null ? screenRepository.findAll() : screenRepository.findByVenueId(venueId);
        return screens.stream().map(screenMapper::toResponse).toList();
    }

    @Override
    public ScreenResponse getScreenById(Long id) {
        return screenMapper.toResponse(getScreen(id));
    }

    @Override
    @Transactional
    public ScreenResponse updateScreen(ScreenUpdateRequest request) {
        Screen screen = getScreen(request.id());
        Venue venue = request.venueId() == null ? screen.getVenue() : getVenue(request.venueId());
        String name = valueOrCurrent(request.name(), screen.getName());

        if (!screen.getName().equalsIgnoreCase(name) || !screen.getVenue().getId().equals(venue.getId())) {
            ensureScreenDoesNotExist(name, venue.getId());
        }

        screen.setName(name);
        screen.setTotalSeats(request.totalSeats() == null ? screen.getTotalSeats() : request.totalSeats());
        screen.setVenue(venue);
        return screenMapper.toResponse(screen);
    }

    @Override
    @Transactional
    public void deleteScreen(Long id) {
        if (!screenRepository.existsById(id)) {
            throw new ResourceNotFoundException("Screen not found with id: " + id);
        }
        screenRepository.deleteById(id);
    }

    private Screen getScreen(Long id) {
        return screenRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Screen not found with id: " + id));
    }

    private Venue getVenue(Long id) {
        return venueRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venue not found with id: " + id));
    }

    private void ensureScreenDoesNotExist(String name, Long venueId) {
        if (screenRepository.existsByNameIgnoreCaseAndVenueId(name.trim(), venueId)) {
            throw new DuplicateResourceException("Screen already exists in this venue");
        }
    }

    private String valueOrCurrent(String incoming, String current) {
        return incoming == null || incoming.isBlank() ? current : incoming.trim();
    }
}
