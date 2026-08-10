package com.guru.seat_inventory_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "show_seats",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_show_seat_number",
                columnNames = {"show_id", "seat_number"}
        ),
        indexes = {
                @Index(name = "idx_show_seats_show_status", columnList = "show_id,status,seat_number"),
                @Index(name = "idx_show_seats_lock", columnList = "show_id,lock_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShowSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "show_id", nullable = false)
    private Long showId;

    @Column(name = "seat_number", nullable = false, length = 20)
    private String seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatStatus status;

    @Column(name = "lock_id", length = 50)
    private String lockId;

    @Column(name = "locked_by_user_id")
    private Long lockedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = SeatStatus.AVAILABLE;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void lock(String newLockId, Long userId) {
        status = SeatStatus.LOCKED;
        lockId = newLockId;
        lockedByUserId = userId;
    }

    public void release() {
        status = SeatStatus.AVAILABLE;
        clearLock();
    }

    public void book() {
        status = SeatStatus.BOOKED;
        clearLock();
    }

    private void clearLock() {
        lockId = null;
        lockedByUserId = null;
    }

    public void block() {
        status = SeatStatus.BLOCKED;
        clearLock();
    }

    public void unblock() {
        status = SeatStatus.AVAILABLE;
        clearLock();
    }
}
