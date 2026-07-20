package com.gururaj.venue_service.repository;

import com.gururaj.venue_service.entity.City;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CityRepository extends JpaRepository<City, Long> {

    boolean existsByNameIgnoreCaseAndStateIgnoreCaseAndCountryIgnoreCase(String name, String state, String country);
}
