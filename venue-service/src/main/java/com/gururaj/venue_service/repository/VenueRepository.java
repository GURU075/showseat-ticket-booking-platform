package com.gururaj.venue_service.repository;

import com.gururaj.venue_service.entity.Venue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VenueRepository extends JpaRepository<Venue, Long> {

    boolean existsByNameIgnoreCaseAndCityId(String name, Long cityId);

    List<Venue> findByCityId(Long cityId);
}
