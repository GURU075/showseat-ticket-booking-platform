package com.gururaj.show_service.repository;

import com.gururaj.show_service.entity.Show;
import com.gururaj.show_service.entity.ShowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ShowRepository extends JpaRepository<Show, Long> {

    List<Show> findAllByOrderByStartTimeAsc();

    List<Show> findByEventIdOrderByStartTimeAsc(Long eventId);

    List<Show> findByScreenIdOrderByStartTimeAsc(Long screenId);

    List<Show> findByEventIdAndScreenIdOrderByStartTimeAsc(Long eventId, Long screenId);

    @Query("""
            select (count(s) > 0) from Show s
            where s.screenId = :screenId
              and s.status <> :cancelledStatus
              and s.startTime < :endTime
              and s.endTime > :startTime
              and (:excludedShowId is null or s.id <> :excludedShowId)
            """)
    boolean existsOverlappingShow(
            @Param("screenId") Long screenId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("cancelledStatus") ShowStatus cancelledStatus,
            @Param("excludedShowId") Long excludedShowId
    );

    @Query(value = "SELECT pg_advisory_xact_lock(:screenId)", nativeQuery = true)
    void lockScreenSchedule(@Param("screenId") Long screenId);
}
