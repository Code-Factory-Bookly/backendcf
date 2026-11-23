package com.bookly.backendcf.booking.presentation.controller;

import com.bookly.backendcf.booking.application.ProfessionalAgendaService;
import com.bookly.backendcf.booking.presentation.dto.ProfessionalAgendaResponse;
import com.bookly.backendcf.professional.infrastructure.persistence.ProfessionalRepository;
import com.bookly.backendcf.schedule.application.ProfessionalNotFoundException;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * HU-14: "Mi Agenda" del profesional.
 *
 * <p>El PROFESSIONAL consulta siempre su propia agenda: el identificador sale del token JWT y
 * cualquier {@code professionalId} enviado en la peticion se ignora, de modo que nadie puede
 * ver la agenda de otra cuenta. El ADMIN no tiene agenda propia: debe indicar de que
 * profesional es mediante {@code professionalId}. Los demas roles no tienen acceso (ver
 * {@code SecurityConfiguration}).
 */
@RestController
@RequestMapping("/api/v1/reservas/profesional")
public class ProfessionalAgendaController {

    private final ProfessionalAgendaService agendaService;
    private final ProfessionalRepository professionalRepository;

    public ProfessionalAgendaController(
            ProfessionalAgendaService agendaService,
            ProfessionalRepository professionalRepository) {
        this.agendaService = agendaService;
        this.professionalRepository = professionalRepository;
    }

    /**
     * Sin fechas devuelve la agenda de hoy (hora de Colombia). {@code from} y {@code to} son
     * fechas {@code yyyy-MM-dd}, ambas inclusivas; con solo {@code from} se consulta ese dia.
     */
    @GetMapping("/agenda")
    public ProfessionalAgendaResponse getAgenda(
            Authentication authentication,
            @RequestParam(required = false) UUID professionalId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return agendaService.find(resolveProfessionalId(authentication, professionalId), from, to);
    }

    private UUID resolveProfessionalId(Authentication authentication, UUID requestedId) {
        if (!isAdmin(authentication)) {
            return UUID.fromString(authentication.getName());
        }
        if (requestedId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El administrador debe indicar el profesional (professionalId)");
        }
        if (!professionalRepository.existsById(requestedId)) {
            throw new ProfessionalNotFoundException(requestedId);
        }
        return requestedId;
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
