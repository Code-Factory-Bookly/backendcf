package com.bookly.backendcf.booking.application;

import com.bookly.backendcf.booking.domain.model.Booking;
import com.bookly.backendcf.booking.domain.model.BookingStatus;
import com.bookly.backendcf.booking.infrastructure.persistence.BookingRepository;
import com.bookly.backendcf.booking.presentation.dto.BookingHistoryPageResponse;
import com.bookly.backendcf.booking.presentation.dto.BookingResponse;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Caso de uso para que un cliente consulte únicamente su propio historial. */
@Service
public class CustomerBookingHistoryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final BookingRepository bookingRepository;
    private final BookingNameDirectory nameDirectory;

    public CustomerBookingHistoryService(
            BookingRepository bookingRepository,
            BookingNameDirectory nameDirectory) {
        this.bookingRepository = bookingRepository;
        this.nameDirectory = nameDirectory;
    }

    @Transactional(readOnly = true)
    public BookingHistoryPageResponse find(
            UUID customerId,
            LocalDateTime from,
            LocalDateTime to,
            BookingStatus status,
            int page,
            int size,
            String order) {
        validate(customerId, from, to, page, size);

        Sort.Direction direction = resolveDirection(order);
        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        new Sort.Order(direction, "startTime"),
                        new Sort.Order(direction, "id")));

        Page<Booking> result = bookingRepository.findCustomerHistory(
                customerId, from, to, status, pageable);

        Map<UUID, String> accountNames = nameDirectory.accountNames(result.getContent().stream()
                .flatMap(booking -> Stream.of(
                        booking.getCustomerId(), booking.getProfessionalId()))
                .distinct()
                .toList());
        Map<UUID, String> serviceNames = nameDirectory.serviceNames(result.getContent().stream()
                .map(Booking::getServiceId)
                .distinct()
                .toList());

        String message = result.isEmpty()
                ? "Aún no cuentas con reservas registradas"
                : null;

        return new BookingHistoryPageResponse(
                result.getContent().stream()
                        .map(booking -> BookingResponse.from(
                                booking,
                                accountNames.get(booking.getCustomerId()),
                                accountNames.get(booking.getProfessionalId()),
                                serviceNames.get(booking.getServiceId())))
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                message);
    }

    private Sort.Direction resolveDirection(String order) {
        if (order == null || order.isBlank()) {
            return Sort.Direction.ASC;
        }
        try {
            return Sort.Direction.fromString(order);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El orden debe ser asc o desc");
        }
    }

    private void validate(
            UUID customerId,
            LocalDateTime from,
            LocalDateTime to,
            int page,
            int size) {
        if (customerId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Se requiere una identidad de cliente válida");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La fecha inicial no puede ser posterior a la fecha final");
        }
        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La página no puede ser negativa");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El tamaño de página debe estar entre 1 y " + MAX_PAGE_SIZE);
        }
    }
}
