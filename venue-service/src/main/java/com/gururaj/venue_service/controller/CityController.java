package com.gururaj.venue_service.controller;

import com.gururaj.venue_service.dto.CityCreateRequest;
import com.gururaj.venue_service.dto.CityResponse;
import com.gururaj.venue_service.dto.CityUpdateRequest;
import com.gururaj.venue_service.service.CityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cities")
@RequiredArgsConstructor
public class CityController {

    private final CityService cityService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CityResponse createCity(@Valid @RequestBody CityCreateRequest request) {
        return cityService.createCity(request);
    }

    @GetMapping
    public List<CityResponse> getAllCities() {
        return cityService.getAllCities();
    }

    @GetMapping("/{id}")
    public CityResponse getCityById(@PathVariable Long id) {
        return cityService.getCityById(id);
    }

    @PutMapping
    public CityResponse updateCity(@Valid @RequestBody CityUpdateRequest request) {
        return cityService.updateCity(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCity(@PathVariable Long id) {
        cityService.deleteCity(id);
    }
}
