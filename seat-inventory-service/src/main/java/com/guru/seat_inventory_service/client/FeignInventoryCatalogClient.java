package com.guru.seat_inventory_service.client;

import com.guru.seat_inventory_service.client.feign.ShowServiceFeignClient;
import com.guru.seat_inventory_service.client.feign.VenueServiceFeignClient;
import com.guru.seat_inventory_service.exception.ExternalServiceException;
import com.guru.seat_inventory_service.exception.ResourceNotFoundException;
import feign.FeignException;
import feign.RetryableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class FeignInventoryCatalogClient implements InventoryCatalogClient {

    private final ShowServiceFeignClient showServiceClient;
    private final VenueServiceFeignClient venueServiceClient;

    @Override
    public ShowSummary getShow(Long showId) {
        ShowServiceFeignClient.ShowServiceResponse response = call(
                () -> showServiceClient.getShow(showId),
                "Show not found with id: " + showId,
                "show-service"
        );
        if (response == null) {
            throw invalidResponse("show-service");
        }
        return new ShowSummary(response.id(), response.venueId(), response.screenId(), response.status());
    }

    @Override
    public ScreenSummary getScreen(Long screenId) {
        VenueServiceFeignClient.ScreenServiceResponse response = call(
                () -> venueServiceClient.getScreen(screenId),
                "Screen not found with id: " + screenId,
                "venue-service"
        );
        if (response == null) {
            throw invalidResponse("venue-service");
        }
        return new ScreenSummary(response.id(), response.venueId());
    }

    @Override
    public List<ScreenSeatSummary> getScreenSeats(Long screenId) {
        List<VenueServiceFeignClient.SeatServiceResponse> response = call(
                () -> venueServiceClient.getScreenSeats(screenId),
                "Screen not found with id: " + screenId,
                "venue-service"
        );
        if (response == null) {
            throw invalidResponse("venue-service");
        }
        if (response.stream().anyMatch(Objects::isNull)) {
            throw new ExternalServiceException("venue-service returned an invalid seat list");
        }
        return response.stream()
                .map(seat -> new ScreenSeatSummary(seat.seatNumber(), seat.screenId()))
                .toList();
    }

    private <T> T call(Supplier<T> request, String notFoundMessage, String serviceName) {
        try {
            return request.get();
        } catch (FeignException.NotFound ex) {
            throw new ResourceNotFoundException(notFoundMessage);
        } catch (RetryableException ex) {
            throw new ExternalServiceException(serviceName + " is unavailable or timed out", ex);
        } catch (FeignException ex) {
            throw new ExternalServiceException(
                    serviceName + " returned HTTP " + ex.status(),
                    ex
            );
        }
    }

    private ExternalServiceException invalidResponse(String serviceName) {
        return new ExternalServiceException(serviceName + " returned an empty response");
    }
}
