package com.gururaj.venue_service.repository;

import com.gururaj.venue_service.entity.Screen;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScreenRepository extends JpaRepository<Screen, Long> {

    boolean existsByNameIgnoreCaseAndVenueId(String name, Long venueId);

    List<Screen> findByVenueId(Long venueId);
}
