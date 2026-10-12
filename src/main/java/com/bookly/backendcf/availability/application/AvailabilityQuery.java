package com.bookly.backendcf.availability.application;

import com.bookly.backendcf.availability.domain.model.BusyPeriod;
import com.bookly.backendcf.availability.domain.model.WorkingWindow;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de consulta usado por el caso de uso de disponibilidad de agenda (HU-07). */
public interface AvailabilityQuery {

    boolean professionalExists(UUID professionalId);

    /** Duración en minutos del servicio; vacío si el servicio no existe o no está ACTIVO. */
    Optional<Integer> findActiveServiceDuration(UUID serviceId);

    List<WorkingWindow> findWorkingWindows(UUID professionalId);

    /**
     * Periodos ocupados del profesional en el rango [from, to). Hoy solo incluye reservas confirmadas; los
     * bloqueos manuales de agenda no existen aún (ver docs/HU-07-disponibilidad.md).
     */
    List<BusyPeriod> findBusyPeriods(UUID professionalId, LocalDateTime from, LocalDateTime to);
}