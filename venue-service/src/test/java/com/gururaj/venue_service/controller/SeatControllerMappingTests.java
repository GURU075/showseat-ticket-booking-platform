package com.gururaj.venue_service.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class SeatControllerMappingTests {

    @Test
    void exposesPhysicalSeatCatalogUnderTheVenueSeatPath() {
        RequestMapping baseMapping = SeatController.class.getAnnotation(RequestMapping.class);

        assertArrayEquals(new String[]{"/api/v1/venue-seats"}, baseMapping.value());
    }
}
