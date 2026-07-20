package com.gururaj.venue_service.mapper;

import com.gururaj.venue_service.dto.VenueCreateRequest;
import com.gururaj.venue_service.dto.VenueResponse;
import com.gururaj.venue_service.entity.Venue;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VenueMapper {

    private final CityMapper cityMapper;

    public Venue toEntity(VenueCreateRequest request) {
        Venue venue = new Venue();
        venue.setName(request.name().trim());
        venue.setAddress(request.address().trim());
        venue.setPincode(request.pincode().trim());
        return venue;
    }

    public VenueResponse toResponse(Venue venue) {
        return new VenueResponse(
                venue.getId(),
                venue.getName(),
                venue.getAddress(),
                venue.getPincode(),
                cityMapper.toResponse(venue.getCity())
        );
    }
}
