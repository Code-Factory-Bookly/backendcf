package com.bookly.backendcf.schedule.presentation;

import com.bookly.backendcf.schedule.application.WeeklyScheduleService;
import com.bookly.backendcf.schedule.presentation.dto.ScheduleRequest;
import com.bookly.backendcf.schedule.presentation.dto.ScheduleResponse;
import com.bookly.backendcf.shared.security.OwnershipGuard;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/horarios")
public class ScheduleController {

    private final WeeklyScheduleService scheduleService;
    private final OwnershipGuard ownershipGuard;

    public ScheduleController(WeeklyScheduleService scheduleService, OwnershipGuard ownershipGuard) {
        this.scheduleService = scheduleService;
        this.ownershipGuard = ownershipGuard;
    }

    @GetMapping("/{professionalId}")
    public ScheduleResponse find(@PathVariable UUID professionalId, Authentication authentication) {
        ownershipGuard.check(professionalId, authentication);
        return scheduleService.find(professionalId);
    }

    @PutMapping("/{professionalId}")
    public ScheduleResponse replace(
            @PathVariable UUID professionalId,
            @Valid @RequestBody ScheduleRequest request,
            Authentication authentication) {
        ownershipGuard.check(professionalId, authentication);
        return scheduleService.replace(professionalId, request);
    }
}
