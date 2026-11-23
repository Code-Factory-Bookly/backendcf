package com.bookly.backendcf.booking.application;

import com.bookly.backendcf.booking.domain.model.Booking;
import com.bookly.backendcf.booking.infrastructure.persistence.BookingRepository;
import com.bookly.backendcf.booking.presentation.dto.ProfessionalAgendaResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Caso de uso "Mi Agenda" del profesional (HU-14).
 *
 * <p>Recibe el identificador del profesional ya resuelto desde el token: es la frontera que
 * garantiza que solo se consulta la agenda propia. Las reservas canceladas no se muestran.
 * "Hoy" se calcula en la zona horaria de Colombia (GMT-5), no en la del servidor.
 */
@Service
public class ProfessionalAgendaService {

    /** Zona horaria con la que se interpreta "hoy" (Colombia, GMT-5, sin horario de verano). */
    public static final ZoneId AGENDA_ZONE = ZoneId.of("America/Bogota");

    /** El listado no esta paginado, por eso se limita el periodo que se puede consultar. */
    public static final int MAX_RANGE_DAYS = 31;

    public static final String MESSAGE_AGENDA_LIBRE_HOY =
            "Tu agenda se encuentra libre para la jornada de hoy";

    public static final String MESSAGE_AGENDA_LIBRE_PERIODO =
            "Tu agenda se encuentra libre en el periodo consultado";

    private final BookingRepository bookingRepository;
    private final BookingNameDirectory nameDirectory;
    private final Clock clock;

    @Autowired
    public ProfessionalAgendaService(
            BookingRepository bookingRepository,
            BookingNameDirectory nameDirectory) {
        this(bookingRepository, nameDirectory, Clock.system(AGENDA_ZONE));
    }

    // Constructor para pruebas con un reloj controlado.
    ProfessionalAgendaService(
            BookingRepository bookingRepository,
            BookingNameDirectory nameDirectory,
            Clock clock) {
        this.bookingRepository = bookingRepository;
        this.nameDirectory = nameDirectory;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ProfessionalAgendaResponse find(UUID professionalId, LocalDate from, LocalDate to) {
        if (professionalId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Se requiere una identidad de profesional válida");
        }

        LocalDate today = LocalDate.now(clock.withZone(AGENDA_ZONE));
        LocalDate start = from != null ? from : today;
        LocalDate end = to != null ? to : start;
        validate(start, end);

        List<Booking> bookings = bookingRepository.findAgenda(
                professionalId,
                start.atStartOfDay(),
                end.plusDays(1).atStartOfDay());

        Map<UUID, String> customerNames = nameDirectory.accountNames(bookings.stream()
                .map(Booking::getCustomerId)
                .distinct()
                .toList());
        Map<UUID, String> serviceNames = nameDirectory.serviceNames(bookings.stream()
                .map(Booking::getServiceId)
                .distinct()
                .toList());

        List<ProfessionalAgendaResponse.Item> items = bookings.stream()
                .map(booking -> toItem(booking, customerNames, serviceNames))
                .toList();

        return new ProfessionalAgendaResponse(
                start, end, items, items.isEmpty() ? emptyMessage(start, end, today) : null);
    }

    private ProfessionalAgendaResponse.Item toItem(
            Booking booking, Map<UUID, String> customerNames, Map<UUID, String> serviceNames) {
        return new ProfessionalAgendaResponse.Item(
                booking.getId(),
                booking.getStartTime(),
                booking.getEndTime(),
                customerNames.get(booking.getCustomerId()),
                serviceNames.get(booking.getServiceId()),
                booking.getStatus().name());
    }

    private String emptyMessage(LocalDate start, LocalDate end, LocalDate today) {
        boolean onlyToday = today.equals(start) && today.equals(end);
        return onlyToday ? MESSAGE_AGENDA_LIBRE_HOY : MESSAGE_AGENDA_LIBRE_PERIODO;
    }

    private void validate(LocalDate start, LocalDate end) {
        if (start.isAfter(end)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La fecha inicial no puede ser posterior a la fecha final");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_RANGE_DAYS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El rango de la agenda no puede superar " + MAX_RANGE_DAYS + " días");
        }
    }
}
