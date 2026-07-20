package com.gururaj.venue_service.repository;

import com.gururaj.venue_service.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    boolean existsBySeatNumberIgnoreCaseAndScreenId(String seatNumber, Long screenId);

    List<Seat> findByScreenIdOrderByRowNameAscSeatIndexAsc(Long screenId);
}
