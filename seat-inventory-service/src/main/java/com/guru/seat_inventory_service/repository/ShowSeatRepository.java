package com.guru.seat_inventory_service.repository;

import com.guru.seat_inventory_service.entity.SeatStatus;
import com.guru.seat_inventory_service.entity.ShowSeat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ShowSeatRepository extends JpaRepository<ShowSeat, Long> {

    List<ShowSeat> findByShowIdOrderBySeatNumberAsc(Long showId);

    List<ShowSeat> findByShowIdAndStatusOrderBySeatNumberAsc(Long showId, SeatStatus status);

    List<ShowSeat> findByShowIdAndConfirmedLockIdOrderBySeatNumberAsc(Long showId, String confirmedLockId);

    boolean existsByShowId(Long showId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select seat from ShowSeat seat
            where seat.showId = :showId and seat.seatNumber in :seatNumbers
            order by seat.seatNumber
            """)
    List<ShowSeat> findRequestedSeatsForUpdate(
            @Param("showId") Long showId,
            @Param("seatNumbers") Collection<String> seatNumbers
    );

}
