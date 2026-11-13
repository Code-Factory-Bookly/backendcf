
package com.bookly.backendcf.resource.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookly.backendcf.catalog.domain.model.ServiceOffering;
import com.bookly.backendcf.catalog.infrastructure.persistence.ServiceOfferingRepository;
import com.bookly.backendcf.resource.domain.model.PhysicalResource;
import com.bookly.backendcf.resource.domain.model.ServiceResource;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ServiceResourcePersistenceTest {

    @Autowired
    private PhysicalResourceRepository resourceRepository;

    @Autowired
    private ServiceOfferingRepository serviceRepository;

    @Autowired
    private ServiceResourceRepository assignmentRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void guardaYConsultaRecursoFisicoEnPostgreSQL() {

        PhysicalResource resource = new PhysicalResource(
                "Cabina integracion " + UUID.randomUUID(),
                3
        );

        PhysicalResource saved = resourceRepository.saveAndFlush(resource);
        UUID resourceId = saved.getId();

        entityManager.clear();

        PhysicalResource found = resourceRepository
                .findById(resourceId)
                .orElseThrow();

        assertThat(resourceId).isNotNull();
        assertThat(found.getName()).isEqualTo(resource.getName());
        assertThat(found.getTotalQuantity()).isEqualTo(3);
    }

    @Test
    void guardaYRecuperaAsociacionEntreServicioYRecurso() {

        ServiceOffering offering = new ServiceOffering(
                "Servicio integracion " + UUID.randomUUID(),
                "Prueba de asociacion HU-11",
                "Pruebas",
                30,
                BigDecimal.valueOf(25000)
        );

        PhysicalResource resource = new PhysicalResource(
                "Recurso integracion " + UUID.randomUUID(),
                2
        );

        ServiceOffering savedService = serviceRepository.saveAndFlush(offering);
        PhysicalResource savedResource = resourceRepository.saveAndFlush(resource);

        UUID serviceId = savedService.getId();
        UUID resourceId = savedResource.getId();

        assignmentRepository.saveAndFlush(
                new ServiceResource(serviceId, resourceId)
        );

        entityManager.clear();

        List<ServiceResource> associations =
                assignmentRepository.findByServiceId(serviceId);

        assertThat(associations).hasSize(1);
        assertThat(associations.get(0).getServiceId()).isEqualTo(serviceId);
        assertThat(associations.get(0).getResourceId()).isEqualTo(resourceId);

        assertThat(
                assignmentRepository.existsByServiceIdAndResourceId(
                        serviceId,
                        resourceId
                )
        ).isTrue();
    }
}
