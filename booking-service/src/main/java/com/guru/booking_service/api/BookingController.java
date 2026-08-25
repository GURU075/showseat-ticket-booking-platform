package com.guru.booking_service.api;

import com.guru.booking_service.service.BookingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<BookingResponse> create(
            @RequestHeader("Idempotency-Key")
            @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:-]{0,127}") String idempotencyKey,
            @Valid @RequestBody CreateBookingRequest request
    ) {
        BookingService.CreationResult result = bookingService.create(request, idempotencyKey);
        if (result.replayed()) {
            return ResponseEntity.ok()
                    .header("Idempotency-Replayed", "true")
                    .body(result.booking());
        }
        return ResponseEntity.created(URI.create("/api/v1/bookings/" + result.booking().id()))
                .header("Idempotency-Replayed", "false")
                .body(result.booking());
    }

    @GetMapping("/{bookingId}")
    public BookingResponse get(@PathVariable UUID bookingId) {
        return bookingService.get(bookingId);
    }
}
