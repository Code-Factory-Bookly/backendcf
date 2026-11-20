package com.bookly.backendcf.schedule.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

/** HU-05: rango [inicio, fin) en el que el profesional no atiende (vacaciones, imprevistos). */
@Entity
@Table(name = "bloqueo_agenda")
public class AgendaBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "profesional_id", nullable = false)
    private UUID professionalId;

    @Column(name = "inicio", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "fin", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "motivo", length = 200)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected AgendaBlock() {
    }

    public AgendaBlock(UUID professionalId, LocalDateTime startTime, LocalDateTime endTime, String reason) {
        this.professionalId = professionalId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.reason = reason;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getProfessionalId() {
        return professionalId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public String getReason() {
        return reason;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
