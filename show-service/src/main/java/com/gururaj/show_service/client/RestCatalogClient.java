package com.gururaj.show_service.client;

import com.gururaj.show_service.exception.ExternalServiceException;
import com.gururaj.show_service.exception.InvalidReferenceException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class RestCatalogClient implements CatalogClient {

    @Qualifier("eventServiceRestClient")
    private final RestClient eventServiceRestClient;

    @Qualifier("venueServiceRestClient")
    private final RestClient venueServiceRestClient;

    @Override
    public void verifyEventExists(String eventId) {
        try {
            EventSummary event = eventServiceRestClient.get()
                    .uri("/api/event/get/{id}", eventId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError,
                            (request, response) -> {
                                throw new InvalidReferenceException("Event not found with id: " + eventId);
                            })
                    .body(EventSummary.class);
            if (event == null || event.id() == null) {
                throw new InvalidReferenceException("Event not found with id: " + eventId);
            }
        } catch (InvalidReferenceException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new ExternalServiceException("Event service is unavailable", ex);
        }
    }

    @Override
    public void verifyVenueExists(Long venueId) {
        try {
            VenueSummary venue = venueServiceRestClient.get()
                    .uri("/api/v1/venues/{id}", venueId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError,
                            (request, response) -> {
                                throw new InvalidReferenceException("Venue not found with id: " + venueId);
                            })
                    .body(VenueSummary.class);
            if (venue == null || venue.id() == null) {
                throw new InvalidReferenceException("Venue not found with id: " + venueId);
            }
        } catch (InvalidReferenceException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new ExternalServiceException("Venue service is unavailable", ex);
        }
    }

    @Override
    public ScreenSummary getScreen(Long screenId) {
        try {
            ScreenSummary screen = venueServiceRestClient.get()
                    .uri("/api/v1/screens/{id}", screenId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError,
                            (request, response) -> {
                                throw new InvalidReferenceException("Screen not found with id: " + screenId);
                            })
                    .body(ScreenSummary.class);
            if (screen == null || screen.id() == null || screen.venueId() == null) {
                throw new InvalidReferenceException("Screen not found with id: " + screenId);
            }
            return screen;
        } catch (InvalidReferenceException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new ExternalServiceException("Venue service is unavailable", ex);
        }
    }

    private record EventSummary(String id) {
    }

    private record VenueSummary(Long id) {
    }
}
