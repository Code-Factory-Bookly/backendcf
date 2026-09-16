package com.bookly.backendcf.catalog.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Lo que un cliente reserva: una limpieza dental, un corte de cabello. Se llama ServiceOffering y
 * no Service para no confundirse con la anotación {@code @Service} de Spring.
 */
@Entity
@Table(name = "servicios")
public class ServiceOffering {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "nombre", nullable = false, length = 150)
    private String name;

    @Column(name = "descripcion", length = 500)
    private String description;

    @Column(name = "categoria", nullable = false, length = 100)
    private String category;

    /** Imprescindible para las agendas: determina cuántos huecos ocupa una cita. */
    @Column(name = "duracion_minutos", nullable = false)
    private int durationMinutes;

    @Column(name = "precio", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private ServiceStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ServiceOffering() {
    }

    /** El estado nace en ACTIVO: lo asigna el sistema, no quien registra el servicio. */
    public ServiceOffering(String name, String description, String category, int durationMinutes, BigDecimal price) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.durationMinutes = durationMinutes;
        this.price = price;
        this.status = ServiceStatus.ACTIVO;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
    }

    /** {@code status} es opcional: si no se envía, se conserva el estado actual. */
    public void update(
            String name, String description, String category, int durationMinutes, BigDecimal price,
            ServiceStatus status) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.durationMinutes = durationMinutes;
        this.price = price;
        if (status != null) {
            this.status = status;
        }
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public ServiceStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
