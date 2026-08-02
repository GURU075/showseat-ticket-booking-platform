package com.gururaj.show_service.controller;

import com.gururaj.show_service.dto.CreateShowRequest;
import com.gururaj.show_service.dto.ShowResponse;
import com.gururaj.show_service.dto.UpdateShowRequest;
import com.gururaj.show_service.service.ShowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/shows")
@RequiredArgsConstructor
public class ShowController {

    private final ShowService showService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShowResponse createShow(@Valid @RequestBody CreateShowRequest request) {
        return showService.createShow(request);
    }

    @GetMapping
    public List<ShowResponse> getShows(
            @RequestParam(required = false) Long eventId,
            @RequestParam(required = false) Long screenId
    ) {
        return showService.getShows(eventId, screenId);
    }

    @GetMapping("/{id}")
    public ShowResponse getShowById(@PathVariable Long id) {
        return showService.getShowById(id);
    }

    @PutMapping("/{id}")
    public ShowResponse updateShow(
            @PathVariable Long id,
            @Valid @RequestBody UpdateShowRequest request
    ) {
        return showService.updateShow(id, request);
    }
}
