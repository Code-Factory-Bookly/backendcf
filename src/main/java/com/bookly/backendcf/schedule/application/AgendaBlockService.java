package com.bookly.backendcf.schedule.application;

import com.bookly.backendcf.schedule.domain.model.AgendaBlock;
import com.bookly.backendcf.schedule.infrastructure.persistence.AgendaBlockRepository;
import com.bookly.backendcf.schedule.presentation.dto.AgendaBlockRequest;
import com.bookly.backendcf.schedule.presentation.dto.AgendaBlockResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU-05: el profesional bloquea un rango de su agenda. Un bloqueo nunca se aplica sobre reservas
 * confirmadas: se rechaza con la lista de citas afectadas para que las cancele o reprograme antes.
 */
@Service
public class AgendaBlockService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE dd/MM", Locale.forLanguageTag("es"));
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    private final AgendaBlockRepository repository;
    private final ConfirmedBookingsLookup confirmedBookings;

    public AgendaBlockService(AgendaBlockRepository repository, ConfirmedBookingsLookup confirmedBookings) {
        this.repository = repository;
        this.confirmedBookings = confirmedBookings;
    }

    /** Bloqueos vigentes o futuros (los que ya terminaron no afectan la agenda). */
    @Transactional(readOnly = true)
    public List<AgendaBlockResponse> findUpcoming(UUID professionalId) {
        return repository.findByProfessionalIdAndEndTimeAfterOrderByStartTimeAsc(professionalId, LocalDateTime.now())
                .stream()
                .map(AgendaBlockResponse::from)
                .toList();
    }

    @Transactional
    public AgendaBlockResponse create(UUID professionalId, AgendaBlockRequest request) {
        validate(request);
        // #212: serializa las solicitudes del mismo profesional; la segunda verá el bloqueo de la primera.
        repository.lockProfessional(professionalId);
        LocalDateTime start = request.startTime();
        LocalDateTime end = request.endTime();

        List<ConfirmedBookingsLookup.AffectedBooking> affected = confirmedBookings.findConfirmed(professionalId, start, end);
        if (!affected.isEmpty()) {
            Map<String, String> details = new LinkedHashMap<>();
            affected.forEach(b -> details.put(b.bookingId().toString(), describe(b)));
            throw AgendaBlockConflictException.bookingsInRange(details);
        }

        List<AgendaBlock> overlapping = repository.findOverlapping(professionalId, start, end);
        if (!overlapping.isEmpty()) {
            throw overlapsBlock(overlapping);
        }

        try {
            String reason = request.reason() == null || request.reason().isBlank() ? null : request.reason().trim();
            return AgendaBlockResponse.from(repository.saveAndFlush(new AgendaBlock(professionalId, start, end, reason)));
        } catch (DataIntegrityViolationException | ConcurrencyFailureException concurrentBlock) {
            // Respaldo: la restricción de exclusión de V50 (o un interbloqueo) rechazó un bloqueo simultáneo.
            throw overlapsBlock(winnersOf(professionalId, start, end));
        }
    }

    // #212: si la transacción ya quedó abortada, la consulta del bloqueo ganador falla; el conflicto
    // sigue siendo AGENDA_BLOCK_OVERLAP, solo que sin el detalle de qué bloqueo ganó.
    private List<AgendaBlock> winnersOf(UUID professionalId, LocalDateTime start, LocalDateTime end) {
        try {
            return repository.findOverlapping(professionalId, start, end);
        } catch (RuntimeException abortedTransaction) {
            return List.of();
        }
    }

    @Transactional
    public void delete(UUID professionalId, UUID blockId) {
        AgendaBlock block = repository.findByIdAndProfessionalId(blockId, professionalId)
                .orElseThrow(() -> new AgendaBlockNotFoundException(blockId));
        repository.delete(block);
    }

    private void validate(AgendaBlockRequest request) {
        Map<String, String> details = new LinkedHashMap<>();
        if (!request.endTime().isAfter(request.startTime())) {
            details.put("endTime", "Debe ser posterior al inicio del bloqueo");
        }
        if (request.startTime().isBefore(LocalDateTime.now())) {
            details.put("startTime", "No se puede bloquear un horario que ya pasó");
        }
        if (!details.isEmpty()) {
            throw new ScheduleValidationException(details);
        }
    }

    private AgendaBlockConflictException overlapsBlock(List<AgendaBlock> blocks) {
        Map<String, String> details = new LinkedHashMap<>();
        blocks.forEach(b -> details.put(b.getId().toString(), range(b.getStartTime(), b.getEndTime())));
        return AgendaBlockConflictException.overlapsBlock(details);
    }

    private String describe(ConfirmedBookingsLookup.AffectedBooking booking) {
        String customer = booking.customerName() == null ? "Cliente" : booking.customerName();
        String service = booking.serviceName() == null ? "Servicio" : booking.serviceName();
        return range(booking.startTime(), booking.endTime()) + " · " + customer + " · " + service;
    }

    private String range(LocalDateTime start, LocalDateTime end) {
        String text = DAY.format(start) + " " + HOUR.format(start) + "–";
        return start.toLocalDate().equals(end.toLocalDate())
                ? text + HOUR.format(end)
                : text + DAY.format(end) + " " + HOUR.format(end);
    }
}
