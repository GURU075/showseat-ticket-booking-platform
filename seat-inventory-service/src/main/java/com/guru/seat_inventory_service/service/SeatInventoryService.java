package com.guru.seat_inventory_service.service;

import com.guru.seat_inventory_service.dto.*;
import com.guru.seat_inventory_service.entity.SeatStatus;

import java.util.List;

public interface SeatInventoryService {

    List<SeatResponse> createSeats(Long showId);

    List<SeatResponse> getSeats(Long showId, SeatStatus status);

    SeatLockResponse lockSeats(LockSeatsRequest request);

    SeatActionResponse confirmSeats(LockActionRequest request);

    SeatActionResponse releaseSeats(LockActionRequest request);

    SeatStatusChangeResponse blockSeats(BlockUnblockRequest request);

    SeatStatusChangeResponse unblockSeats(BlockUnblockRequest request);
}
