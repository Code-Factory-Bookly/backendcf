
package com.bookly.backendcf.resource.domain.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class ServiceResourceId implements Serializable {

    private UUID serviceId;
    private UUID resourceId;

    protected ServiceResourceId() {
    }

    public ServiceResourceId(UUID serviceId, UUID resourceId) {
        this.serviceId = serviceId;
        this.resourceId = resourceId;
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ServiceResourceId other)) {
            return false;
        }
        return Objects.equals(serviceId, other.serviceId)
                && Objects.equals(resourceId, other.resourceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceId, resourceId);
    }
}
