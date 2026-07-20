package com.gururaj.venue_service.service.impl;

import com.gururaj.venue_service.dto.CityCreateRequest;
import com.gururaj.venue_service.dto.CityResponse;
import com.gururaj.venue_service.dto.CityUpdateRequest;
import com.gururaj.venue_service.entity.City;
import com.gururaj.venue_service.exception.DuplicateResourceException;
import com.gururaj.venue_service.exception.ResourceNotFoundException;
import com.gururaj.venue_service.mapper.CityMapper;
import com.gururaj.venue_service.repository.CityRepository;
import com.gururaj.venue_service.service.CityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CityServiceImpl implements CityService {

    private final CityRepository cityRepository;
    private final CityMapper cityMapper;

    @Override
    @Transactional
    public CityResponse createCity(CityCreateRequest request) {
        ensureCityDoesNotExist(request.name(), request.state(), request.country());
        City city = cityRepository.save(cityMapper.toEntity(request));
        return cityMapper.toResponse(city);
    }

    @Override
    public List<CityResponse> getAllCities() {
        return cityRepository.findAll().stream()
                .map(cityMapper::toResponse)
                .toList();
    }

    @Override
    public CityResponse getCityById(Long id) {
        return cityMapper.toResponse(getCityEntity(id));
    }

    @Override
    @Transactional
    public CityResponse updateCity(CityUpdateRequest request) {
        City city = getCityEntity(request.id());
        String name = valueOrCurrent(request.name(), city.getName());
        String state = valueOrCurrent(request.state(), city.getState());
        String country = valueOrCurrent(request.country(), city.getCountry());

        if (isChanged(city, name, state, country)) {
            ensureCityDoesNotExist(name, state, country);
        }

        city.setName(name);
        city.setState(state);
        city.setCountry(country);
        return cityMapper.toResponse(city);
    }

    @Override
    @Transactional
    public void deleteCity(Long id) {
        if (!cityRepository.existsById(id)) {
            throw new ResourceNotFoundException("City not found with id: " + id);
        }
        cityRepository.deleteById(id);
    }

    private City getCityEntity(Long id) {
        return cityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("City not found with id: " + id));
    }

    private void ensureCityDoesNotExist(String name, String state, String country) {
        if (cityRepository.existsByNameIgnoreCaseAndStateIgnoreCaseAndCountryIgnoreCase(name.trim(), state.trim(), country.trim())) {
            throw new DuplicateResourceException("City already exists for the given state and country");
        }
    }

    private boolean isChanged(City city, String name, String state, String country) {
        return !city.getName().equalsIgnoreCase(name)
                || !city.getState().equalsIgnoreCase(state)
                || !city.getCountry().equalsIgnoreCase(country);
    }

    private String valueOrCurrent(String incoming, String current) {
        return incoming == null || incoming.isBlank() ? current : incoming.trim();
    }
}
