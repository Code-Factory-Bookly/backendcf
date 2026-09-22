package com.bookly.backendcf.catalog.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookly.backendcf.catalog.domain.model.ServiceOffering;
import com.bookly.backendcf.catalog.domain.model.ServiceStatus;
import com.bookly.backendcf.catalog.infrastructure.persistence.ServiceOfferingRepository;
import com.bookly.backendcf.catalog.presentation.dto.ServiceOfferingRequest;
import com.bookly.backendcf.catalog.presentation.dto.ServiceOfferingResponse;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Cubre los escenarios Gherkin de HU-02 (Historias_de_usuario_azure.md) a nivel de servicio: el
 * gap señalado en README_QA.md sección 4/8 ("Ninguna [prueba] sobre ServiceOfferingService, la
 * lógica de duplicado por nombre").
 */
@ExtendWith(MockitoExtension.class)
class ServiceOfferingServiceTest {

    @Mock
    private ServiceOfferingRepository serviceOfferingRepository;

    private ServiceOfferingService service;

    @BeforeEach
    void setUp() {
        service = new ServiceOfferingService(serviceOfferingRepository);
    }

    // HU-02 - Escenario Gherkin "Registro correcto de un servicio en el catálogo": con datos
    // válidos y nombre no repetido, el servicio debe guardarse con estado ACTIVO.
    @Test
    void registroCorrectoGuardaElServicioEnElCatalogoConEstadoActivo() {
        when(serviceOfferingRepository.existsByNameIgnoreCase("Limpieza dental")).thenReturn(false);
        when(serviceOfferingRepository.save(any(ServiceOffering.class))).thenAnswer(invocation -> {
            ServiceOffering offering = invocation.getArgument(0);
            ReflectionTestUtils.setField(offering, "id", java.util.UUID.randomUUID());
            return offering;
        });

        ServiceOfferingRequest request = new ServiceOfferingRequest(
                "  Limpieza   dental  ", "  Profesional  ", "  Odontología  ", 45, BigDecimal.valueOf(80000), null);

        ServiceOfferingResponse response = service.create(request);

        assertEquals("Limpieza dental", response.name());
        assertEquals("Odontología", response.category());
        assertEquals(ServiceStatus.ACTIVO, response.status());

        ArgumentCaptor<ServiceOffering> saved = ArgumentCaptor.forClass(ServiceOffering.class);
        verify(serviceOfferingRepository).save(saved.capture());
        assertEquals("Limpieza dental", saved.getValue().getName());
        assertEquals(ServiceStatus.ACTIVO, saved.getValue().getStatus());
    }

    // HU-02 - Caso de duplicado (lógica de negocio detrás del escenario "datos obligatorios
    // faltantes" y de la unicidad implícita del catálogo): si ya existe un servicio con ese
    // nombre, se rechaza sin llegar a guardar.
    @Test
    void registroConNombreYaExistenteEnElCatalogoEsRechazado() {
        when(serviceOfferingRepository.existsByNameIgnoreCase("Manicure")).thenReturn(true);

        ServiceOfferingRequest request = new ServiceOfferingRequest(
                "Manicure", null, "Belleza", 30, BigDecimal.valueOf(20000), null);

        assertThrows(ServiceOfferingAlreadyExistsException.class, () -> service.create(request));

        verify(serviceOfferingRepository, never()).save(any());
    }

    // HU-02 - Escenario Gherkin "Consulta pública del catálogo": sin filtro de categoría debe
    // devolver todo el catálogo ordenado por nombre.
    @Test
    void consultaSinFiltroDeCategoriaDevuelveTodoElCatalogo() {
        ServiceOffering corte = new ServiceOffering("Corte", null, "Peluquería", 30, BigDecimal.valueOf(15000));
        when(serviceOfferingRepository.findAllByOrderByNameAsc()).thenReturn(List.of(corte));

        List<ServiceOfferingResponse> result = service.list(null);

        assertEquals(1, result.size());
        assertEquals("Corte", result.get(0).name());
        verify(serviceOfferingRepository, never()).findAllByCategoryIgnoreCaseOrderByNameAsc(any());
    }

    // HU-02 - Escenario Gherkin "Consulta pública del catálogo": "puedo filtrar los resultados por
    // categoría".
    @Test
    void consultaConCategoriaFiltraPorEsaCategoria() {
        ServiceOffering limpieza =
                new ServiceOffering("Limpieza dental", null, "Odontología", 45, BigDecimal.valueOf(80000));
        when(serviceOfferingRepository.findAllByCategoryIgnoreCaseOrderByNameAsc("Odontología"))
                .thenReturn(List.of(limpieza));

        List<ServiceOfferingResponse> result = service.list("  Odontología  ");

        assertEquals(1, result.size());
        assertEquals("Odontología", result.get(0).category());
        verify(serviceOfferingRepository).findAllByCategoryIgnoreCaseOrderByNameAsc(eq("Odontología"));
        verify(serviceOfferingRepository, never()).findAllByOrderByNameAsc();
    }

    // HU-02 - Caso de control: una categoría en blanco debe tratarse igual que "sin filtro", no
    // como un filtro por cadena vacía.
    @Test
    void consultaConCategoriaEnBlancoSeComportaComoSinFiltro() {
        when(serviceOfferingRepository.findAllByOrderByNameAsc()).thenReturn(List.of());

        List<ServiceOfferingResponse> result = service.list("   ");

        assertTrue(result.isEmpty());
        verify(serviceOfferingRepository).findAllByOrderByNameAsc();
        verify(serviceOfferingRepository, never()).findAllByCategoryIgnoreCaseOrderByNameAsc(any());
    }
}
