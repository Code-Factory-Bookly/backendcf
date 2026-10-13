package com.bookly.backendcf.professional.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Qué profesional ofrece qué servicio del catálogo. Guarda solo los UUID, igual que
 * {@code Booking} referencia cliente/profesional/servicio: evita acoplar este módulo a las
 * entidades JPA de {@code catalog}.
 */
@Entity
@Table(name = "profesional_servicio")
public class ProfessionalServiceAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "profesional_id", nullable = false)
    private UUID professionalId;

    @Column(name = "servicio_id", nullable = false)
    private UUID serviceId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected ProfessionalServiceAssignment() {
    }

    public ProfessionalServiceAssignment(UUID professionalId, UUID serviceId) {
        this.professionalId = professionalId;
        this.serviceId = serviceId;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getProfessionalId() {
        return professionalId;
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
