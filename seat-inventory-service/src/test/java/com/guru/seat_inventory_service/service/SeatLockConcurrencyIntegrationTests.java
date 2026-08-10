package com.guru.seat_inventory_service.service;

import com.guru.seat_inventory_service.dto.BlockUnblockRequest;
import com.guru.seat_inventory_service.dto.LockSeatsRequest;
import com.guru.seat_inventory_service.entity.SeatStatus;
import com.guru.seat_inventory_service.entity.ShowSeat;
import com.guru.seat_inventory_service.exception.SeatConflictException;
import com.guru.seat_inventory_service.repository.ShowSeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
class SeatLockConcurrencyIntegrationTests {

    @Autowired
    private SeatInventoryService seatInventoryService;

    @Autowired
    private ShowSeatRepository showSeatRepository;

    @BeforeEach
    void setUp() {
        showSeatRepository.deleteAll();
        showSeatRepository.saveAndFlush(
                ShowSeat.builder()
                        .showId(101L)
                        .seatNumber("A1")
                        .status(SeatStatus.AVAILABLE)
                        .build()
        );
    }

    @Test
    void onlyOneConcurrentRequestCanLockTheSameSeat() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() -> attemptLock(start, 501L));
            Future<String> second = executor.submit(() -> attemptLock(start, 502L));
            start.countDown();

            List<String> outcomes = List.of(first.get(), second.get());
            assertEquals(1, outcomes.stream().filter("LOCKED"::equals).count());
            assertEquals(1, outcomes.stream().filter("CONFLICT"::equals).count());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void onlyBlockOrCustomerLockCanWinForTheSameSeat() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> lock = executor.submit(() -> attemptLock(start, 501L));
            Future<String> block = executor.submit(() -> attemptBlock(start));
            start.countDown();

            List<String> outcomes = List.of(lock.get(), block.get());
            assertEquals(1, outcomes.stream().filter("CONFLICT"::equals).count());
            assertEquals(
                    1,
                    outcomes.stream().filter(outcome -> !"CONFLICT".equals(outcome)).count()
            );

            ShowSeat seat = showSeatRepository.findByShowIdOrderBySeatNumberAsc(101L).get(0);
            if (seat.getStatus() == SeatStatus.BLOCKED) {
                assertNull(seat.getLockId());
                assertNull(seat.getLockedByUserId());
            } else {
                assertEquals(SeatStatus.LOCKED, seat.getStatus());
                assertEquals(501L, seat.getLockedByUserId());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private String attemptLock(CountDownLatch start, Long userId) throws InterruptedException {
        start.await();
        try {
            seatInventoryService.lockSeats(new LockSeatsRequest(101L, userId, List.of("A1")));
            return "LOCKED";
        } catch (SeatConflictException ex) {
            return "CONFLICT";
        }
    }

    private String attemptBlock(CountDownLatch start) throws InterruptedException {
        start.await();
        try {
            seatInventoryService.blockSeats(new BlockUnblockRequest(101L, List.of("A1")));
            return "BLOCKED";
        } catch (SeatConflictException ex) {
            return "CONFLICT";
        }
    }
}
