
package com.bookly.backendcf.resource.infrastructure.persistence;

import com.bookly.backendcf.resource.domain.model.ServiceResource;
import com.bookly.backendcf.resource.domain.model.ServiceResourceId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceResourceRepository
        extends JpaRepository<ServiceResource, ServiceResourceId> {

    List<ServiceResource> findByServiceId(UUID serviceId);

    List<ServiceResource> findByResourceId(UUID resourceId);

    boolean existsByServiceIdAndResourceId(UUID serviceId, UUID resourceId);
}
