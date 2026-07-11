package com.guru.event_service.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class Event {
    @Id
    private String id;

    private String title;

    private String description;

    private String language;

    private Integer durationMinutes;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;


}
