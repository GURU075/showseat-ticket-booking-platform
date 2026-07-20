package com.gururaj.venue_service.service.impl;

import com.gururaj.venue_service.dto.VenueCreateRequest;
import com.gururaj.venue_service.dto.VenueResponse;
import com.gururaj.venue_service.dto.VenueUpdateRequest;
import com.gururaj.venue_service.entity.City;
import com.gururaj.venue_service.entity.Venue;
import com.gururaj.venue_service.exception.DuplicateResourceException;
import com.gururaj.venue_service.exception.ResourceNotFoundException;
import com.gururaj.venue_service.mapper.VenueMapper;
import com.gururaj.venue_service.repository.CityRepository;
import com.gururaj.venue_service.repository.VenueRepository;
import com.gururaj.venue_service.service.VenueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VenueServiceImpl implements VenueService {

    private final VenueRepository venueRepository;
    private final CityRepository cityRepository;
    private final VenueMapper venueMapper;

    @Override
    @Transactional
    public VenueResponse createVenue(VenueCreateRequest request) {
        City city = getCity(request.cityId());
        ensureVenueDoesNotExist(request.name(), city.getId());

        Venue venue = venueMapper.toEntity(request);
        venue.setCity(city);
        return venueMapper.toResponse(venueRepository.save(venue));
    }

    @Override
    public List<VenueResponse> getAllVenues(Long cityId) {
        List<Venue> venues = cityId == null ? venueRepository.findAll() : venueRepository.findByCityId(cityId);
        return venues.stream().map(venueMapper::toResponse).toList();
    }

    @Override
    public VenueResponse getVenueById(Long id) {
        return venueMapper.toResponse(getVenue(id));
    }

    @Override
    @Transactional
    public VenueResponse updateVenue(VenueUpdateRequest request) {
        Venue venue = getVenue(request.id());
        City city = request.cityId() == null ? venue.getCity() : getCity(request.cityId());
        String name = valueOrCurrent(request.name(), venue.getName());

        if (!venue.getName().equalsIgnoreCase(name) || !venue.getCity().getId().equals(city.getId())) {
            ensureVenueDoesNotExist(name, city.getId());
        }

        venue.setName(name);
        venue.setAddress(valueOrCurrent(request.address(), venue.getAddress()));
        venue.setPincode(valueOrCurrent(request.pincode(), venue.getPincode()));
        venue.setCity(city);
        return venueMapper.toResponse(venue);
    }

    @Override
    @Transactional
    public void deleteVenue(Long id) {
        if (!venueRepository.existsById(id)) {
            throw new ResourceNotFoundException("Venue not found with id: " + id);
        }
        venueRepository.deleteById(id);
    }

    private Venue getVenue(Long id) {
        return venueRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venue not found with id: " + id));
    }

    private City getCity(Long id) {
        return cityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("City not found with id: " + id));
    }

    private void ensureVenueDoesNotExist(String name, Long cityId) {
        if (venueRepository.existsByNameIgnoreCaseAndCityId(name.trim(), cityId)) {
            throw new DuplicateResourceException("Venue already exists in this city");
        }
    }

    private String valueOrCurrent(String incoming, String current) {
        return incoming == null || incoming.isBlank() ? current : incoming.trim();
    }
}
