
package com.bookly.backendcf.resource.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ServiceResourceIdTest {

    @Test
    void identificadoresConLosMismosValoresSonIguales() {
        UUID serviceId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        ServiceResourceId first =
                new ServiceResourceId(serviceId, resourceId);

        ServiceResourceId second =
                new ServiceResourceId(serviceId, resourceId);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void identificadoresConServiciosDiferentesNoSonIguales() {
        UUID resourceId = UUID.randomUUID();

        ServiceResourceId first =
                new ServiceResourceId(UUID.randomUUID(), resourceId);

        ServiceResourceId second =
                new ServiceResourceId(UUID.randomUUID(), resourceId);

        assertNotEquals(first, second);
    }

    @Test
    void identificadoresConRecursosDiferentesNoSonIguales() {
        UUID serviceId = UUID.randomUUID();

        ServiceResourceId first =
                new ServiceResourceId(serviceId, UUID.randomUUID());

        ServiceResourceId second =
                new ServiceResourceId(serviceId, UUID.randomUUID());

        assertNotEquals(first, second);
    }

    @Test
    void identificadorEsIgualASiMismo() {
        ServiceResourceId id =
                new ServiceResourceId(UUID.randomUUID(), UUID.randomUUID());

        assertTrue(id.equals(id));
    }

    @Test
    void identificadorNoEsIgualANullNiAOtroTipo() {
        ServiceResourceId id =
                new ServiceResourceId(UUID.randomUUID(), UUID.randomUUID());

        assertFalse(id.equals(null));
        assertFalse(id.equals("otro tipo"));
    }

    @Test
    void gettersDevuelvenLosIdentificadoresCorrectos() {
        UUID serviceId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        ServiceResourceId id =
                new ServiceResourceId(serviceId, resourceId);

        assertEquals(serviceId, id.getServiceId());
        assertEquals(resourceId, id.getResourceId());
    }
}
