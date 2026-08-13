package com.guru.seat_inventory_service.lock;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface SeatLockStore {

    SeatLockDetails acquire(Long showId, Long userId, List<String> seatNumbers);

    Set<String> findLockedSeatNumbers(Long showId, Collection<String> seatNumbers);

    SeatLockDetails verifyAndExtend(Long showId, Long userId, String lockId);

    SeatLockDetails release(Long showId, Long userId, String lockId);
}
