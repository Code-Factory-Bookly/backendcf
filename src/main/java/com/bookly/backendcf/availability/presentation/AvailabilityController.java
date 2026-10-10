package com.bookly.backendcf.availability.presentation;

import com.bookly.backendcf.availability.application.AvailabilityService;
import com.bookly.backendcf.availability.presentation.dto.AvailabilityResponse;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/disponibilidad")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    public AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    // Los parámetros son required = false a propósito: si faltara uno, Spring reenviaría a /error y la
    // seguridad lo convertiría en 401 (ver MantisBT BUG-002 en GlobalExceptionHandler). El servicio los
    // valida y responde el 400 uniforme.
    @GetMapping
    public AvailabilityResponse find(
            @RequestParam(name = "profesionalId", required = false) UUID professionalId,
            @RequestParam(name = "servicioId", required = false) UUID serviceId,
            @RequestParam(name = "desde", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "hasta", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return availabilityService.find(professionalId, serviceId, from, to);
    }
}