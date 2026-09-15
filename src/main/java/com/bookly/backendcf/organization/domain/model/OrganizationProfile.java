package com.bookly.backendcf.organization.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Perfil público de la organización: lo que ven los clientes al buscar citas. La plataforma
 * atiende a una sola organización, así que existe como máximo una fila.
 */
@Entity
@Table(name = "organization_profile")
public class OrganizationProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "commercial_name", nullable = false, length = 200)
    private String commercialName;

    @Column(name = "contact_email", nullable = false, length = 320)
    private String contactEmail;

    @Column(name = "contact_phone", nullable = false, length = 30)
    private String contactPhone;

    @Column(nullable = false, length = 300)
    private String address;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected OrganizationProfile() {
    }

    public OrganizationProfile(String commercialName, String contactEmail, String contactPhone, String address) {
        this.commercialName = commercialName;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.address = address;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void update(String commercialName, String contactEmail, String contactPhone, String address) {
        this.commercialName = commercialName;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.address = address;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public String getCommercialName() {
        return commercialName;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public String getAddress() {
        return address;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
