package com.guru.event_service.entity;

import jakarta.persistence.*;
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

    @PrePersist
    public void onCreate(){
        this.setCreatedAt(LocalDateTime.now());
        this.setUpdatedAt(LocalDateTime.now());
        normaliseFields();
    }
    @PreUpdate
    public void onUpdate(){
        this.setUpdatedAt(LocalDateTime.now());
        normaliseFields();

    }
    private void normaliseFields(){
        if(status!=null){
            this.status = status.trim().toUpperCase();
        }
    }


}
