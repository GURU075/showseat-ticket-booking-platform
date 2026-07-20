package com.gururaj.venue_service.service;

import com.gururaj.venue_service.dto.CityCreateRequest;
import com.gururaj.venue_service.dto.CityResponse;
import com.gururaj.venue_service.dto.CityUpdateRequest;

import java.util.List;

public interface CityService {

    CityResponse createCity(CityCreateRequest request);

    List<CityResponse> getAllCities();

    CityResponse getCityById(Long id);

    CityResponse updateCity(CityUpdateRequest request);

    void deleteCity(Long id);
}
