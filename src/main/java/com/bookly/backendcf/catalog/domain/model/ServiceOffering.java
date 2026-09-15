package com.bookly.backendcf.catalog.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Lo que un cliente reserva: una limpieza dental, un corte de cabello. Se llama ServiceOffering y
 * no Service para no confundirse con la anotación {@code @Service} de Spring.
 */
@Entity
@Table(name = "service_offering")
public class ServiceOffering {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "specialty_id", nullable = false)
    private Specialty specialty;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    /** Imprescindible para las agendas: determina cuántos huecos ocupa una cita. */
    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ServiceOffering() {
    }

    public ServiceOffering(
            Specialty specialty, String name, String description, int durationMinutes, BigDecimal price) {
        this.specialty = specialty;
        this.name = name;
        this.description = description;
        this.durationMinutes = durationMinutes;
        this.price = price;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void update(
            Specialty specialty, String name, String description, int durationMinutes, BigDecimal price) {
        this.specialty = specialty;
        this.name = name;
        this.description = description;
        this.durationMinutes = durationMinutes;
        this.price = price;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public Specialty getSpecialty() {
        return specialty;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
