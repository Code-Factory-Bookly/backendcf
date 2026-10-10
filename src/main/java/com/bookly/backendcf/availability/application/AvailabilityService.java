package com.bookly.backendcf.availability.application;

import com.bookly.backendcf.availability.domain.model.AvailabilityCalculator;
import com.bookly.backendcf.availability.domain.model.AvailableSlot;
import com.bookly.backendcf.availability.domain.model.BusyPeriod;
import com.bookly.backendcf.availability.domain.model.WorkingWindow;
import com.bookly.backendcf.catalog.application.ServiceOfferingNotFoundException;
import com.bookly.backendcf.schedule.application.ProfessionalNotFoundException;
import com.bookly.backendcf.availability.presentation.dto.AvailabilityResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Caso de uso de HU-07: consulta de los bloques libres de un profesional para un servicio. */
@Service
public class AvailabilityService {

    static final int MAX_RANGE_DAYS = 31;
    static final String EMPTY_MESSAGE = "El profesional no tiene horarios libres en el rango seleccionado. "
            + "Amplía el filtro de fechas o elige otro profesional.";

    private final AvailabilityQuery availabilityQuery;

    public AvailabilityService(AvailabilityQuery availabilityQuery) {
        this.availabilityQuery = availabilityQuery;
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse find(UUID professionalId, UUID serviceId, LocalDate from, LocalDate to) {
        validate(professionalId, serviceId, from, to);

        if (!availabilityQuery.professionalExists(professionalId)) {
            throw new ProfessionalNotFoundException(professionalId);
        }
        // Un servicio inexistente o INACTIVO no se puede reservar: ambos responden 404.
        int durationMinutes = availabilityQuery.findActiveServiceDuration(serviceId)
                .orElseThrow(() -> new ServiceOfferingNotFoundException(serviceId));

        List<WorkingWindow> windows = availabilityQuery.findWorkingWindows(professionalId);
        List<BusyPeriod> busyPeriods = availabilityQuery.findBusyPeriods(
                professionalId, from.atStartOfDay(), to.plusDays(1).atStartOfDay());

        Duration duration = Duration.ofMinutes(durationMinutes);
        LocalDateTime now = LocalDateTime.now();
        List<AvailabilityResponse.Day> days = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            List<AvailableSlot> free = AvailabilityCalculator.freeSlots(date, windows, duration, busyPeriods, now);
            if (!free.isEmpty()) {
                days.add(AvailabilityResponse.Day.from(date, free));
            }
        }

        boolean available = !days.isEmpty();
        return new AvailabilityResponse(
                professionalId, serviceId, durationMinutes, from, to,
                available, available ? null : EMPTY_MESSAGE, days);
    }

    private void validate(UUID professionalId, UUID serviceId, LocalDate from, LocalDate to) {
        Map<String, String> details = new LinkedHashMap<>();
        if (professionalId == null) {
            details.put("profesionalId", "Es obligatorio");
        }
        if (serviceId == null) {
            details.put("servicioId", "Es obligatorio");
        }
        if (from == null) {
            details.put("desde", "Es obligatorio");
        }
        if (to == null) {
            details.put("hasta", "Es obligatorio");
        }
        if (!details.isEmpty()) {
            throw new InvalidAvailabilityQueryException("Faltan parámetros de la consulta", details);
        }

        if (to.isBefore(from)) {
            throw new InvalidAvailabilityQueryException(
                    "La fecha inicial no puede ser posterior a la fecha final",
                    Map.of("hasta", "Debe ser mayor o igual que desde"));
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw new InvalidAvailabilityQueryException(
                    "El rango de fechas es demasiado amplio",
                    Map.of("hasta", "El rango máximo es de " + MAX_RANGE_DAYS + " días"));
        }
        if (to.isBefore(LocalDate.now())) {
            throw new InvalidAvailabilityQueryException(
                    "El rango de fechas no puede estar en el pasado",
                    Map.of("hasta", "Debe ser hoy o una fecha futura"));
        }
    }
}