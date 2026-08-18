package com.guru.seat_inventory_service.client;

import com.guru.seat_inventory_service.client.feign.VenueServiceFeignClient;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class VenueServiceFeignClientMappingTests {

    @Test
    void requestsPhysicalSeatsFromTheVenueSeatCatalogPath() throws NoSuchMethodException {
        GetMapping mapping = VenueServiceFeignClient.class
                .getMethod("getScreenSeats", Long.class)
                .getAnnotation(GetMapping.class);

        assertArrayEquals(new String[]{"/api/v1/venue-seats"}, mapping.value());
    }
}
