
package com.bookly.backendcf.resource.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * HU-11: Recurso físico limitado asociado a los servicios de Bookly.
 */
@Entity
@Table(name = "recursos_fisicos")
public class PhysicalResource {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "nombre", nullable = false, length = 150)
    private String name;

    @Column(name = "cantidad_total", nullable = false)
    private int totalQuantity;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected PhysicalResource() {
    }

    public PhysicalResource(String name, int totalQuantity) {
        validateQuantity(totalQuantity);

        this.name = name;
        this.totalQuantity = totalQuantity;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void update(String name, int totalQuantity) {
        validateQuantity(totalQuantity);

        this.name = name;
        this.totalQuantity = totalQuantity;
        this.updatedAt = OffsetDateTime.now();
    }

    private void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "El inventario del recurso debe ser un número entero mayor a cero"
            );
        }
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getTotalQuantity() {
        return totalQuantity;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
