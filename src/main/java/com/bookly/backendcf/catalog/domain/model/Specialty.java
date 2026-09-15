package com.bookly.backendcf.catalog.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Área bajo la que se agrupan servicios: Ortodoncia, Endodoncia, Barbería. */
@Entity
@Table(name = "specialty")
public class Specialty {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Specialty() {
    }

    public Specialty(String name, String description) {
        this.name = name;
        this.description = description;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void update(String name, String description) {
        this.name = name;
        this.description = description;
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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
