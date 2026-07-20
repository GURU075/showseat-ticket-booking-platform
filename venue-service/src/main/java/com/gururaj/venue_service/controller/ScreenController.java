package com.gururaj.venue_service.controller;

import com.gururaj.venue_service.dto.ScreenCreateRequest;
import com.gururaj.venue_service.dto.ScreenResponse;
import com.gururaj.venue_service.dto.ScreenUpdateRequest;
import com.gururaj.venue_service.service.ScreenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/screens")
@RequiredArgsConstructor
public class ScreenController {

    private final ScreenService screenService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ScreenResponse createScreen(@Valid @RequestBody ScreenCreateRequest request) {
        return screenService.createScreen(request);
    }

    @GetMapping
    public List<ScreenResponse> getAllScreens(@RequestParam(required = false) Long venueId) {
        return screenService.getAllScreens(venueId);
    }

    @GetMapping("/{id}")
    public ScreenResponse getScreenById(@PathVariable Long id) {
        return screenService.getScreenById(id);
    }

    @PutMapping
    public ScreenResponse updateScreen(@Valid @RequestBody ScreenUpdateRequest request) {
        return screenService.updateScreen(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteScreen(@PathVariable Long id) {
        screenService.deleteScreen(id);
    }
}
