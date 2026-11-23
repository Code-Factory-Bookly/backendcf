
package com.bookly.backendcf.booking.application.service;

import com.bookly.backendcf.booking.application.exception.BookingConflictException;
import com.bookly.backendcf.booking.domain.events.BookingCreatedEvent;
import com.bookly.backendcf.booking.domain.model.Booking;
import com.bookly.backendcf.booking.domain.model.BookingStatus;
import com.bookly.backendcf.booking.infrastructure.persistence.BookingRepository;
import com.bookly.backendcf.booking.infrastructure.event.BookingEventPublisher;
import com.bookly.backendcf.catalog.application.ServiceOfferingNotFoundException;
import com.bookly.backendcf.catalog.infrastructure.persistence.ServiceOfferingRepository;
import com.bookly.backendcf.resource.application.ResourceCapacityValidator;
import com.bookly.backendcf.schedule.application.AgendaBlocks;
import com.bookly.backendcf.schedule.infrastructure.persistence.WeeklySlotRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class CreateBookingService {

    private static final String SLOT_OCCUPIED = "SLOT_OCUPADO";

    private static final String SLOT_OCCUPIED_MESSAGE =
            "El horario seleccionado ya no está disponible";

    private static final String BOOKING_UNIQUE_CONSTRAINT =
            "unique_booking_per_professional_time";

    private static final String BOOKING_OVERLAP_CONSTRAINT =
            "booking_no_overlapping_confirmed";

    private final BookingRepository bookingRepository;
    private final BookingEventPublisher eventPublisher;
    private final WeeklySlotRepository weeklySlotRepository;
    private final AgendaBlocks agendaBlocks;
    private final ResourceCapacityValidator resourceCapacityValidator;
    private final ServiceOfferingRepository serviceOfferingRepository;

    // Constructor original: compatibilidad con pruebas anteriores.
    public CreateBookingService(
            BookingRepository bookingRepository,
            BookingEventPublisher eventPublisher,
            WeeklySlotRepository weeklySlotRepository
    ) {
        this(
                bookingRepository,
                eventPublisher,
                weeklySlotRepository,
                AgendaBlocks.NONE,
                null,
                null
        );
    }

    // Constructor HU-05: compatibilidad con pruebas de bloqueos de agenda.
    public CreateBookingService(
            BookingRepository bookingRepository,
            BookingEventPublisher eventPublisher,
            WeeklySlotRepository weeklySlotRepository,
            AgendaBlocks agendaBlocks
    ) {
        this(
                bookingRepository,
                eventPublisher,
                weeklySlotRepository,
                agendaBlocks,
                null,
                null
        );
    }

    // Constructor de compatibilidad para pruebas y consumidores existentes.
    public CreateBookingService(
            BookingRepository bookingRepository,
            BookingEventPublisher eventPublisher,
            WeeklySlotRepository weeklySlotRepository,
            AgendaBlocks agendaBlocks,
            ResourceCapacityValidator resourceCapacityValidator
    ) {
        this(
                bookingRepository,
                eventPublisher,
                weeklySlotRepository,
                agendaBlocks,
                resourceCapacityValidator,
                null
        );
    }

    // Constructor principal de Spring: integra HU-05, HU-12 y el precio histórico.
    @Autowired
    public CreateBookingService(
            BookingRepository bookingRepository,
            BookingEventPublisher eventPublisher,
            WeeklySlotRepository weeklySlotRepository,
            AgendaBlocks agendaBlocks,
            ResourceCapacityValidator resourceCapacityValidator,
            ServiceOfferingRepository serviceOfferingRepository
    ) {
        this.bookingRepository = bookingRepository;
        this.eventPublisher = eventPublisher;
        this.weeklySlotRepository = weeklySlotRepository;
        this.agendaBlocks = agendaBlocks;
        this.resourceCapacityValidator = resourceCapacityValidator;
        this.serviceOfferingRepository = serviceOfferingRepository;
    }

    @Transactional
    public Booking createBooking(
            UUID customerId,
            UUID professionalId,
            UUID serviceId,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {

        // Validaciones existentes.
        validateTimeRange(startTime, endTime);
        validateFutureTime(startTime);
        validateServiceDuration(serviceId, startTime, endTime);

        validateWithinProfessionalSchedule(
                professionalId,
                startTime,
                endTime
        );

        // HU-05: comprobar bloqueos de agenda.
        validateNotBlocked(
                professionalId,
                startTime,
                endTime
        );

        // Comprobar reservas cruzadas del mismo profesional.
        List<Booking> overlapping =
                bookingRepository.findOverlappingBookings(
                        professionalId,
                        startTime,
                        endTime
                );

        if (!overlapping.isEmpty()) {
            throw slotOccupied();
        }

        try {

            // HU-12: validar capacidad fisica antes de confirmar.
            // Se ejecuta dentro de la misma transaccion.
            if (resourceCapacityValidator != null) {
                resourceCapacityValidator.validate(
                        serviceId,
                        startTime,
                        endTime
                );
            }

            Booking booking = new Booking(
                    customerId,
                    professionalId,
                    serviceId,
                    startTime,
                    endTime,
                    resolvePriceAtBooking(serviceId)
            );

            booking.setStatus(BookingStatus.CONFIRMADA);

            // Forzar INSERT y detectar errores de unicidad.
            Booking saved =
                    bookingRepository.saveAndFlush(booking);

            BookingCreatedEvent event =
                    new BookingCreatedEvent(
                            saved.getId(),
                            saved.getCustomerId(),
                            saved.getProfessionalId(),
                            saved.getServiceId(),
                            saved.getStartTime(),
                            saved.getEndTime()
                    );

            eventPublisher.publish(event);

            return saved;

        } catch (DataIntegrityViolationException e) {

            if (isBookingConflictConstraintViolation(e)) {
                throw slotOccupied();
            }

            throw e;

        } catch (CannotAcquireLockException e) {

            // Una disputa por bloqueos puede impedir confirmar la reserva.
            throw slotOccupied();
        }
    }

    private java.math.BigDecimal resolvePriceAtBooking(UUID serviceId) {
        if (serviceOfferingRepository == null) {
            return null;
        }
        return serviceOfferingRepository.findById(serviceId)
                .orElseThrow(() -> new ServiceOfferingNotFoundException(serviceId))
                .getPrice();
    }

    private BookingConflictException slotOccupied() {
        return new BookingConflictException(
                SLOT_OCCUPIED,
                SLOT_OCCUPIED_MESSAGE
        );
    }

    private boolean isBookingConflictConstraintViolation(
            Throwable exception
    ) {

        Throwable current = exception;

        while (current != null) {

            if (current instanceof
                    org.hibernate.exception.ConstraintViolationException) {

                org.hibernate.exception.ConstraintViolationException
                        constraintException =
                        (org.hibernate.exception.ConstraintViolationException)
                                current;

                String constraintName =
                        constraintException.getConstraintName();

                if (isBookingConflictConstraint(constraintName)) {
                    return true;
                }
            }

            String message = current.getMessage();

            if (message != null
                    && containsBookingConflictConstraint(message)) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }

    private boolean isBookingConflictConstraint(
            String constraintName
    ) {
        return constraintName != null
                && (
                constraintName.equalsIgnoreCase(
                        BOOKING_UNIQUE_CONSTRAINT
                )
                || constraintName.equalsIgnoreCase(
                        BOOKING_OVERLAP_CONSTRAINT
                )
        );
    }

    private boolean containsBookingConflictConstraint(
            String message
    ) {
        String normalized =
                message.toLowerCase(java.util.Locale.ROOT);

        return normalized.contains(
                BOOKING_UNIQUE_CONSTRAINT
        ) || normalized.contains(
                BOOKING_OVERLAP_CONSTRAINT
        );
    }

    private void validateTimeRange(
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {

        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException(
                    "Las fechas de inicio y fin son obligatorias"
            );
        }

        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException(
                    "startTime debe ser antes que endTime"
            );
        }
    }

    // #219: la disponibilidad ofrece huecos del largo del servicio; una reserva más corta o más larga
    // liberaría o tomaría tiempo que no corresponde al servicio contratado.
    private void validateServiceDuration(
            UUID serviceId,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        if (serviceOfferingRepository == null) {
            return;
        }
        serviceOfferingRepository.findById(serviceId).ifPresent(service -> {
            long minutes = java.time.Duration.between(startTime, endTime).toMinutes();
            if (minutes != service.getDurationMinutes()) {
                throw new IllegalArgumentException(
                        "La reserva debe durar " + service.getDurationMinutes()
                                + " minutos, igual que el servicio");
            }
        });
    }

    private void validateFutureTime(
            LocalDateTime startTime
    ) {

        if (startTime.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException(
                    "No puedes agendar en el pasado"
            );
        }
    }

    // HU-05: validar bloqueos del profesional.
    private void validateNotBlocked(
            UUID professionalId,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {

        if (!agendaBlocks.findOverlapping(
                professionalId,
                startTime,
                endTime
        ).isEmpty()) {

            throw new BookingConflictException(
                    "SLOT_BLOCKED",
                    "El profesional bloqueó ese horario en su agenda"
            );
        }
    }

    private void validateWithinProfessionalSchedule(
            UUID professionalId,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {

        boolean containedInSchedule =
                startTime.toLocalDate()
                        .equals(endTime.toLocalDate())

                && weeklySlotRepository
                        .findByProfessionalIdOrderByDayOfWeekAscStartTimeAsc(
                                professionalId
                        )
                        .stream()

                        .filter(slot ->
                                slot.getDayOfWeek()
                                        .equals(startTime.getDayOfWeek())
                        )

                        .anyMatch(slot ->
                                !startTime.toLocalTime()
                                        .isBefore(slot.getStartTime())

                                && !endTime.toLocalTime()
                                        .isAfter(slot.getEndTime())
                        );

        if (!containedInSchedule) {
            throw new BookingConflictException(
                    "SLOT_OUTSIDE_SCHEDULE",
                    "El horario seleccionado está fuera de la disponibilidad del profesional"
            );
        }
    }
}
