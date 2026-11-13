
package com.bookly.backendcf.resource.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * HU-11: Asociación entre un servicio y un recurso físico.
 * Utiliza la clave primaria compuesta definida en V56.
 */
@Entity
@Table(name = "servicio_recurso")
@IdClass(ServiceResourceId.class)
public class ServiceResource {

    @Id
    @Column(name = "servicio_id", nullable = false)
    private UUID serviceId;

    @Id
    @Column(name = "recurso_id", nullable = false)
    private UUID resourceId;

    protected ServiceResource() {
    }

    public ServiceResource(UUID serviceId, UUID resourceId) {
        this.serviceId = java.util.Objects.requireNonNull(
                serviceId, "El identificador del servicio es obligatorio");

        this.resourceId = java.util.Objects.requireNonNull(
                resourceId, "El identificador del recurso es obligatorio");
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public UUID getResourceId() {
        return resourceId;
    }
}
