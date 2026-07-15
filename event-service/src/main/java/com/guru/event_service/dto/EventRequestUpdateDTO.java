package com.guru.event_service.dto;

import jakarta.validation.constraints.NotBlank;

public record EventRequestUpdateDTO(
        @NotBlank(message = "Id is required")
        String id,
                String title,
                String description,
                String language,
                Integer durationMinutes,
                String status
) {
}
