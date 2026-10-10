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

    /** Reservas confirmadas del profesional que se solapan con el rango [from, to). */
    List<BusyPeriod> findBusyPeriods(UUID professionalId, LocalDateTime from, LocalDateTime to);
}