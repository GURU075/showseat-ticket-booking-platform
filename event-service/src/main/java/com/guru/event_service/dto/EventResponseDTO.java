package com.guru.event_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventResponseDTO {
    private String id;

    private String title;

    private String description;

    private String language;

    private Integer durationMinutes;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
