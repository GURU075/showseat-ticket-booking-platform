package com.gururaj.venue_service.mapper;

import com.gururaj.venue_service.dto.CityCreateRequest;
import com.gururaj.venue_service.dto.CityResponse;
import com.gururaj.venue_service.entity.City;
import org.springframework.stereotype.Component;

@Component
public class CityMapper {

    public City toEntity(CityCreateRequest request) {
        City city = new City();
        city.setName(request.name().trim());
        city.setState(request.state().trim());
        city.setCountry(request.country().trim());
        return city;
    }

    public CityResponse toResponse(City city) {
        return new CityResponse(city.getId(), city.getName(), city.getState(), city.getCountry());
    }
}
