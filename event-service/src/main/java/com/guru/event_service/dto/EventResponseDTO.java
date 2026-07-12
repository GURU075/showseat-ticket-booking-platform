package com.guru.event_service.dto;

import java.time.LocalDateTime;

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
