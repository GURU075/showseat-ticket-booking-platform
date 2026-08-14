package com.guru.seat_inventory_service.controller;

import com.guru.seat_inventory_service.dto.BlockUnblockRequest;
import com.guru.seat_inventory_service.dto.LockActionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class SeatInventoryControllerMappingTests {

    @Test
    void exposesSeatCommandsAtTheExpectedRoutes() throws NoSuchMethodException {
        RequestMapping baseMapping = SeatInventoryController.class.getAnnotation(RequestMapping.class);
        assertArrayEquals(new String[]{"/api/v1/seats"}, baseMapping.value());

        assertPostMapping("createSeats", Long.class, "/shows/{showId}");
        assertPostMapping("releaseSeats", LockActionRequest.class, "/release");
        assertPostMapping("blockSeats", BlockUnblockRequest.class, "/block");
        assertPostMapping("unblockSeats", BlockUnblockRequest.class, "/unblock");
    }

    private void assertPostMapping(
            String methodName,
            Class<?> requestType,
            String expectedPath
    ) throws NoSuchMethodException {
        PostMapping mapping = SeatInventoryController.class
                .getMethod(methodName, requestType)
                .getAnnotation(PostMapping.class);
        assertArrayEquals(new String[]{expectedPath}, mapping.value());
    }
}
