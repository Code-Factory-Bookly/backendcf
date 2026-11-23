package com.bookly.backendcf.booking.presentation.controller;

import com.bookly.backendcf.audit.application.AuditRecorder;
import com.bookly.backendcf.audit.domain.model.AuditAction;
import com.bookly.backendcf.audit.domain.model.AuditActionType;
import com.bookly.backendcf.audit.domain.model.AuditResourceType;
import com.bookly.backendcf.booking.application.BookingNameDirectory;
import com.bookly.backendcf.booking.application.CustomerBookingHistoryService;
import com.bookly.backendcf.booking.application.service.CreateBookingService;
import com.bookly.backendcf.booking.application.exception.BookingConflictException;
import com.bookly.backendcf.booking.domain.model.Booking;
import com.bookly.backendcf.booking.domain.model.BookingStatus;
import com.bookly.backendcf.booking.infrastructure.persistence.BookingRepository;
import com.bookly.backendcf.booking.presentation.dto.BookingHistoryPageResponse;
import com.bookly.backendcf.booking.presentation.dto.BookingResponse;
import com.bookly.backendcf.booking.presentation.dto.CreateBookingRequest;
import com.bookly.backendcf.booking.presentation.dto.CreateBookingResponse;
import com.bookly.backendcf.shared.error.ApiErrorResponse;
import com.bookly.backendcf.shared.security.OwnershipGuard;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.UUID;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

@RestController
@RequestMapping("/api/v1/reservas")
public class BookingController {

    private final CreateBookingService createBookingService;
    private final BookingRepository bookingRepository;
    private final OwnershipGuard ownershipGuard;
    private final BookingNameDirectory nameDirectory;
    private final CustomerBookingHistoryService customerBookingHistoryService;
    private final AuditRecorder auditRecorder;

    public BookingController(
            CreateBookingService createBookingService,
            BookingRepository bookingRepository,
            OwnershipGuard ownershipGuard,
            BookingNameDirectory nameDirectory,
            CustomerBookingHistoryService customerBookingHistoryService,
            AuditRecorder auditRecorder) {
        this.createBookingService = createBookingService;
        this.bookingRepository = bookingRepository;
        this.ownershipGuard = ownershipGuard;
        this.nameDirectory = nameDirectory;
        this.customerBookingHistoryService = customerBookingHistoryService;
        this.auditRecorder = auditRecorder;
    }

    @GetMapping("/cliente/historial")
    public BookingHistoryPageResponse getOwnHistory(
            Authentication authentication,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "asc") String order) {
        UUID customerId = UUID.fromString(authentication.getName());
        return customerBookingHistoryService.find(
                customerId, from, to, status, page, size, order);
    }

    @GetMapping("/cliente/{customerId}")
    public List<BookingResponse> getByCustomer(
            @PathVariable UUID customerId, Authentication authentication, HttpServletRequest request) {
        ownershipGuard.check(customerId, authentication);
        List<BookingResponse> history = withNames(bookingRepository.findByCustomerId(customerId));
        auditThirdPartyRead(customerId, authentication, request);
        return history;
    }

    // #216: cuando un administrador lee el historial de otro cliente queda rastro en la auditoría
    // (quién, de quién, desde qué IP y cuándo). La lectura del propio historial no se audita.
    private void auditThirdPartyRead(UUID customerId, Authentication authentication, HttpServletRequest request) {
        UUID actorId = UUID.fromString(authentication.getName());
        if (actorId.equals(customerId)) {
            return;
        }
        auditRecorder.record(new AuditAction(
                actorId,
                AuditActionType.CUSTOMER_HISTORY_VIEWED,
                AuditResourceType.USER,
                customerId,
                OffsetDateTime.now(),
                clientIp(request),
                Map.of("route", "/api/v1/reservas/cliente/{customerId}")));
    }

    // Detrás del proxy de Render la IP real llega en la última entrada de X-Forwarded-For.
    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String[] hops = forwardedFor.split(",");
            return hops[hops.length - 1].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Obsoleta desde HU-14: usar {@code GET /reservas/profesional/agenda}. Se mantiene solo para
     * no romper a los consumidores actuales (el frontend la usa); se retirara cuando migren.
     * No esta paginada, no filtra por fecha y devuelve tambien las reservas canceladas.
     */
    @GetMapping("/profesional/{professionalId}")
    public List<BookingResponse> getByProfessional(
            @PathVariable UUID professionalId, Authentication authentication) {
        ownershipGuard.check(professionalId, authentication);
        return withNames(bookingRepository.findByProfessionalId(professionalId));
    }

    @PostMapping
    public ResponseEntity<?> createBooking(@Valid @RequestBody CreateBookingRequest request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            UUID customerId = UUID.fromString(auth.getName());

            Booking booking = createBookingService.createBooking(
                    customerId,
                    request.getProfessionalId(),
                    request.getServiceId(),
                    request.getStartTime(),
                    request.getEndTime()
            );

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new CreateBookingResponse(
                            booking.getId(),
                            booking.getCustomerId(),
                            booking.getProfessionalId(),
                            booking.getServiceId(),
                            booking.getStartTime(),
                            booking.getEndTime(),
                            booking.getStatus().toString(),
                            booking.getCreatedAt()
                    ));
        } catch (BookingConflictException e) {
            // #218: mismo contrato de error que el resto de la API (errorCode, message, details, traceId, timestamp).
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiErrorResponse.of(e.getErrorCode(), e.getMessage(), Map.of()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiErrorResponse.of("INVALID_REQUEST", e.getMessage(), Map.of()));
        }
    }

    private List<BookingResponse> withNames(List<Booking> bookings) {
        Map<UUID, String> accountNames = nameDirectory.accountNames(bookings.stream()
                .flatMap(b -> Stream.of(b.getCustomerId(), b.getProfessionalId()))
                .distinct()
                .toList());
        Map<UUID, String> serviceNames = nameDirectory.serviceNames(bookings.stream()
                .map(Booking::getServiceId)
                .distinct()
                .toList());
        return bookings.stream()
                .map(b -> BookingResponse.from(
                        b,
                        accountNames.get(b.getCustomerId()),
                        accountNames.get(b.getProfessionalId()),
                        serviceNames.get(b.getServiceId())))
                .toList();
    }

}
